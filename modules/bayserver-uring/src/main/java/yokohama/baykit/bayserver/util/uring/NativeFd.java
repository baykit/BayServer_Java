package yokohama.baykit.bayserver.util.uring;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * Utility to extract native file descriptors from Java NIO channels
 * and to perform low-level fd operations via Panama FFI.
 *
 * Requires: --add-opens java.base/sun.nio.ch=ALL-UNNAMED
 */
public final class NativeFd {

    private NativeFd() {}

    ////////////////////////////////////////////
    // Native syscall handles
    ////////////////////////////////////////////
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

    private static final MethodHandle readHandle;
    private static final MethodHandle writeHandle;
    private static final MethodHandle closeHandle;
    private static final MethodHandle fcntlHandle;
    private static final MethodHandle getpeernameHandle;
    private static final MethodHandle getsocknameHandle;
    private static final MethodHandle setsockoptHandle;

    static {
        try {
            readHandle = LINKER.downcallHandle(
                    LOOKUP.find("read").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,   // fd
                            ValueLayout.ADDRESS,    // buf
                            ValueLayout.JAVA_LONG   // count
                    ));

            writeHandle = LINKER.downcallHandle(
                    LOOKUP.find("write").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,   // fd
                            ValueLayout.ADDRESS,    // buf
                            ValueLayout.JAVA_LONG   // count
                    ));

            closeHandle = LINKER.downcallHandle(
                    LOOKUP.find("close").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT    // fd
                    ));

            fcntlHandle = LINKER.downcallHandle(
                    LOOKUP.find("fcntl").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,   // fd
                            ValueLayout.JAVA_INT,   // cmd
                            ValueLayout.JAVA_INT    // arg
                    ),
                    Linker.Option.firstVariadicArg(2)
            );

            getpeernameHandle = LINKER.downcallHandle(
                    LOOKUP.find("getpeername").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,   // sockfd
                            ValueLayout.ADDRESS,    // addr
                            ValueLayout.ADDRESS     // addrlen
                    ));

            getsocknameHandle = LINKER.downcallHandle(
                    LOOKUP.find("getsockname").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,   // sockfd
                            ValueLayout.ADDRESS,    // addr
                            ValueLayout.ADDRESS     // addrlen
                    ));

            setsockoptHandle = LINKER.downcallHandle(
                    LOOKUP.find("setsockopt").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,   // sockfd
                            ValueLayout.JAVA_INT,   // level
                            ValueLayout.JAVA_INT,   // optname
                            ValueLayout.ADDRESS,    // optval
                            ValueLayout.JAVA_INT    // optlen
                    ));
        }
        catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    ////////////////////////////////////////////
    // Reflection for fd extraction
    ////////////////////////////////////////////
    private static Method getFDValMethod;
    private static Field fdField;

    static {
        try {
            // Try sun.nio.ch.SelChImpl.getFDVal() which is available on SocketChannel, ServerSocketChannel
            Class<?> selChImpl = Class.forName("sun.nio.ch.SelChImpl");
            getFDValMethod = selChImpl.getMethod("getFDVal");
            getFDValMethod.setAccessible(true);
        }
        catch (Exception e) {
            // Fallback: try to get fd from FileDescriptor
            try {
                fdField = java.io.FileDescriptor.class.getDeclaredField("fd");
                fdField.setAccessible(true);
            }
            catch (Exception e2) {
                throw new ExceptionInInitializerError(
                        "Cannot access native fd: " + e.getMessage() + " / " + e2.getMessage());
            }
        }
    }

    /**
     * Extract the native file descriptor from a Java NIO Channel.
     * Supports ServerSocketChannel, SocketChannel, DatagramChannel.
     */
    public static int getFd(Channel ch) throws IOException {
        try {
            if (getFDValMethod != null && ch instanceof SelectableChannel) {
                return (int) getFDValMethod.invoke(ch);
            }
            throw new IOException("Cannot extract fd from channel: " + ch.getClass().getName());
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException("Failed to extract fd from channel", e);
        }
    }

    ////////////////////////////////////////////
    // POSIX syscall wrappers
    ////////////////////////////////////////////

    /**
     * POSIX read(2).
     * @return bytes read, 0 on EOF, or negative errno
     */
    public static long read(int fd, MemorySegment buf, long count) throws IOException {
        try {
            return (long) readHandle.invoke(fd, buf, count);
        }
        catch (Throwable e) {
            throw new IOException("read failed", e);
        }
    }

    /**
     * POSIX write(2).
     * @return bytes written, or negative errno
     */
    public static long write(int fd, MemorySegment buf, long count) throws IOException {
        try {
            return (long) writeHandle.invoke(fd, buf, count);
        }
        catch (Throwable e) {
            throw new IOException("write failed", e);
        }
    }

    /**
     * POSIX close(2).
     */
    public static void close(int fd) throws IOException {
        try {
            int ret = (int) closeHandle.invoke(fd);
            if (ret < 0) {
                throw new IOException("close failed for fd=" + fd);
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("close failed", e);
        }
    }

    // Socket option constants
    private static final int SOL_TCP = 6;       // IPPROTO_TCP
    private static final int TCP_NODELAY = 1;

    /**
     * Set TCP_NODELAY on a socket fd (disable Nagle algorithm).
     */
    public static void setTcpNoDelay(int fd, boolean enable) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment optval = arena.allocate(ValueLayout.JAVA_INT);
            optval.set(ValueLayout.JAVA_INT, 0, enable ? 1 : 0);
            int ret = (int) setsockoptHandle.invoke(fd, SOL_TCP, TCP_NODELAY, optval, 4);
            if (ret < 0) {
                throw new IOException("setsockopt TCP_NODELAY failed for fd=" + fd);
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("setsockopt failed", e);
        }
    }

    /**
     * Set a file descriptor to non-blocking mode via fcntl.
     */
    public static void setNonBlocking(int fd) throws IOException {
        try {
            int flags = (int) fcntlHandle.invoke(fd, IoUringConstants.F_GETFL, 0);
            if (flags < 0) {
                throw new IOException("fcntl F_GETFL failed for fd=" + fd);
            }
            int ret = (int) fcntlHandle.invoke(fd, IoUringConstants.F_SETFL, flags | IoUringConstants.O_NONBLOCK);
            if (ret < 0) {
                throw new IOException("fcntl F_SETFL failed for fd=" + fd);
            }
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("fcntl failed", e);
        }
    }

    /**
     * Get peer address of a socket fd using getpeername(2).
     * @param arena arena for sockaddr allocation
     * @return native sockaddr segment
     */
    public static MemorySegment getpeername(int fd, Arena arena) throws IOException {
        MemorySegment addr = SockAddr.allocateStorage(arena);
        MemorySegment addrLen = SockAddr.allocateAddrLen(arena);
        try {
            int ret = (int) getpeernameHandle.invoke(fd, addr, addrLen);
            if (ret < 0) {
                throw new IOException("getpeername failed for fd=" + fd);
            }
            return addr;
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("getpeername failed", e);
        }
    }

    /**
     * Get local address of a socket fd using getsockname(2).
     * @param arena arena for sockaddr allocation
     * @return native sockaddr segment
     */
    public static MemorySegment getsockname(int fd, Arena arena) throws IOException {
        MemorySegment addr = SockAddr.allocateStorage(arena);
        MemorySegment addrLen = SockAddr.allocateAddrLen(arena);
        try {
            int ret = (int) getsocknameHandle.invoke(fd, addr, addrLen);
            if (ret < 0) {
                throw new IOException("getsockname failed for fd=" + fd);
            }
            return addr;
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("getsockname failed", e);
        }
    }

    ////////////////////////////////////////////
    // High-level ByteBuffer-based wrappers
    // (These hide MemorySegment from callers)
    ////////////////////////////////////////////

    /**
     * Read from fd into a ByteBuffer.
     * @return bytes read, or -1 on EOF
     */
    public static int readToByteBuffer(int fd, ByteBuffer buf) throws IOException {
        int remaining = buf.remaining();
        if (remaining == 0)
            return 0;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBuf = arena.allocate(remaining);
            long n = read(fd, nativeBuf, remaining);

            if (n == 0)
                return -1; // EOF

            if (n < 0)
                throw new IOException("read failed: errno=" + (-n));

            for (int i = 0; i < (int) n; i++) {
                buf.put(nativeBuf.get(ValueLayout.JAVA_BYTE, i));
            }
            return (int) n;
        }
    }

    /**
     * Write from a ByteBuffer to fd.
     * @return bytes written
     */
    public static int writeFromByteBuffer(int fd, ByteBuffer buf) throws IOException {
        int remaining = buf.remaining();
        if (remaining == 0)
            return 0;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBuf = arena.allocate(remaining);

            int pos = buf.position();
            for (int i = 0; i < remaining; i++) {
                nativeBuf.set(ValueLayout.JAVA_BYTE, i, buf.get(pos + i));
            }

            long n = write(fd, nativeBuf, remaining);
            if (n < 0)
                throw new IOException("write failed: errno=" + (-n));

            buf.position(pos + (int) n);
            return (int) n;
        }
    }

    /**
     * Get remote address as InetSocketAddress.
     */
    public static InetSocketAddress getRemoteAddress(int fd) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = getpeername(fd, arena);
            return SockAddr.fromNative(addr);
        }
    }

    /**
     * Get local address as InetSocketAddress.
     */
    public static InetSocketAddress getLocalAddress(int fd) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = getsockname(fd, arena);
            return SockAddr.fromNative(addr);
        }
    }
}
