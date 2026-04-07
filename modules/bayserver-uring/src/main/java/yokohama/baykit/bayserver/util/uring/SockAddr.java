package yokohama.baykit.bayserver.util.uring;

import java.lang.foreign.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static yokohama.baykit.bayserver.util.uring.IoUringConstants.*;

/**
 * Utility for converting between Java InetSocketAddress and native struct sockaddr.
 */
public final class SockAddr {

    private SockAddr() {}

    ////////////////////////////////////////////
    // sockaddr_in layout (16 bytes)
    ////////////////////////////////////////////
    public static final StructLayout SOCKADDR_IN_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("sin_family"),      // 0
            ValueLayout.JAVA_SHORT.withName("sin_port"),        // 2  (network byte order)
            ValueLayout.JAVA_INT.withName("sin_addr"),          // 4  (network byte order)
            MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE).withName("sin_zero") // 8
    );

    ////////////////////////////////////////////
    // sockaddr_in6 layout (28 bytes)
    ////////////////////////////////////////////
    public static final StructLayout SOCKADDR_IN6_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT.withName("sin6_family"),     // 0
            ValueLayout.JAVA_SHORT.withName("sin6_port"),       // 2  (network byte order)
            ValueLayout.JAVA_INT.withName("sin6_flowinfo"),     // 4
            MemoryLayout.sequenceLayout(16, ValueLayout.JAVA_BYTE).withName("sin6_addr"), // 8
            ValueLayout.JAVA_INT.withName("sin6_scope_id")      // 24
    );

    /**
     * Convert InetSocketAddress to native sockaddr.
     * @param arena arena for allocation
     * @param addr Java socket address
     * @return native sockaddr memory segment
     */
    public static MemorySegment toNative(Arena arena, InetSocketAddress addr) {
        InetAddress inetAddr = addr.getAddress();

        if (inetAddr instanceof Inet4Address) {
            MemorySegment sockaddr = arena.allocate(SOCKADDR_IN_LAYOUT);
            sockaddr.fill((byte) 0);
            sockaddr.set(ValueLayout.JAVA_SHORT, 0, AF_INET);
            sockaddr.set(ValueLayout.JAVA_SHORT, 2, htons((short) addr.getPort()));
            byte[] rawAddr = inetAddr.getAddress();
            sockaddr.set(ValueLayout.JAVA_BYTE, 4, rawAddr[0]);
            sockaddr.set(ValueLayout.JAVA_BYTE, 5, rawAddr[1]);
            sockaddr.set(ValueLayout.JAVA_BYTE, 6, rawAddr[2]);
            sockaddr.set(ValueLayout.JAVA_BYTE, 7, rawAddr[3]);
            return sockaddr;
        }
        else if (inetAddr instanceof Inet6Address) {
            MemorySegment sockaddr = arena.allocate(SOCKADDR_IN6_LAYOUT);
            sockaddr.fill((byte) 0);
            sockaddr.set(ValueLayout.JAVA_SHORT, 0, AF_INET6);
            sockaddr.set(ValueLayout.JAVA_SHORT, 2, htons((short) addr.getPort()));
            byte[] rawAddr = inetAddr.getAddress();
            MemorySegment addrSlice = sockaddr.asSlice(8, 16);
            for (int i = 0; i < 16; i++) {
                addrSlice.set(ValueLayout.JAVA_BYTE, i, rawAddr[i]);
            }
            return sockaddr;
        }
        else {
            throw new IllegalArgumentException("Unsupported address type: " + inetAddr.getClass());
        }
    }

    /**
     * Get the size of the native sockaddr for an InetSocketAddress.
     */
    public static int nativeSize(InetSocketAddress addr) {
        if (addr.getAddress() instanceof Inet6Address) {
            return SIZEOF_SOCKADDR_IN6;
        }
        return SIZEOF_SOCKADDR_IN;
    }

    /**
     * Convert native sockaddr to InetSocketAddress.
     * @param sockaddr native sockaddr memory segment
     * @return Java socket address
     */
    public static InetSocketAddress fromNative(MemorySegment sockaddr) throws UnknownHostException {
        short family = sockaddr.get(ValueLayout.JAVA_SHORT, 0);
        int port = Short.toUnsignedInt(ntohs(sockaddr.get(ValueLayout.JAVA_SHORT, 2)));

        if (family == AF_INET) {
            byte[] rawAddr = new byte[4];
            rawAddr[0] = sockaddr.get(ValueLayout.JAVA_BYTE, 4);
            rawAddr[1] = sockaddr.get(ValueLayout.JAVA_BYTE, 5);
            rawAddr[2] = sockaddr.get(ValueLayout.JAVA_BYTE, 6);
            rawAddr[3] = sockaddr.get(ValueLayout.JAVA_BYTE, 7);
            return new InetSocketAddress(InetAddress.getByAddress(rawAddr), port);
        }
        else if (family == AF_INET6) {
            byte[] rawAddr = new byte[16];
            for (int i = 0; i < 16; i++) {
                rawAddr[i] = sockaddr.get(ValueLayout.JAVA_BYTE, 8 + i);
            }
            return new InetSocketAddress(InetAddress.getByAddress(rawAddr), port);
        }
        else {
            throw new IllegalArgumentException("Unsupported address family: " + family);
        }
    }

    /**
     * Allocate a sockaddr_storage buffer for accepting connections.
     */
    public static MemorySegment allocateStorage(Arena arena) {
        MemorySegment storage = arena.allocate(SIZEOF_SOCKADDR_STORAGE, 8);
        storage.fill((byte) 0);
        return storage;
    }

    /**
     * Allocate a socklen_t (int) for accept.
     */
    public static MemorySegment allocateAddrLen(Arena arena) {
        MemorySegment addrLen = arena.allocate(ValueLayout.JAVA_INT);
        addrLen.set(ValueLayout.JAVA_INT, 0, SIZEOF_SOCKADDR_STORAGE);
        return addrLen;
    }

    ////////////////////////////////////////////
    // Byte order conversion (big-endian)
    ////////////////////////////////////////////

    private static short htons(short val) {
        return Short.reverseBytes(val);
    }

    private static short ntohs(short val) {
        return Short.reverseBytes(val);
    }
}
