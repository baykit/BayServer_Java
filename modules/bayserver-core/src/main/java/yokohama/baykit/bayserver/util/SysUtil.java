package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SysUtil {

    public static boolean supportFork() {
        return false;
    }

    public static boolean supportSelectFile() {
        return false;
    }

    public static boolean supportNonblockFileRead() {
        return true;
    }

    public static boolean supportNonblockFileWrite() {
        return false;
    }

    public static boolean supportSelectPipe() {
        return false;
    }

    public static boolean supportNonblockPipeRead() {
        return true;
    }

    public static boolean runOnWindows() {
        return System.getProperty("os.name").toLowerCase().startsWith("win");
    }

    public static long pid() {
        long pid = -1;
        try {
            Class processHandleClass = Class.forName("java.lang.ProcessHandle");
            Method m = processHandleClass.getMethod("current");
            Object processHandle = m.invoke(null);
            m = processHandleClass.getMethod("pid");
            pid = (Long)m.invoke(processHandle);
        }
        catch(Exception e) {
            BayLog.debug("%s", e);
        }

        if(pid != -1) {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            pid = Long.parseLong(name.split("@")[0]);
        }

        return pid;
    }

    public static boolean supportUnixDomainSocketAddress() {
        try {
            Class c = Class.forName("java.net.UnixDomainSocketAddress");
            Method m = c.getMethod("of", String.class);
            return true;
        }
        catch(Throwable e) {
            BayLog.error(e);
            return false;
        }
    }

    public static SocketAddress getUnixDomainSocketAddress(String path) throws IOException{
        try {
            Class c = Class.forName("java.net.UnixDomainSocketAddress");
            Method m = c.getMethod("of", String.class);

            return (SocketAddress)m.invoke(null, path);
        }
        catch(Exception e) {
            throw new IOException("Cannot create unix domain socket", e);
        }
    }

    public static ServerSocketChannel openUnixDomainServerSocketChannel() throws IOException {

        try {
            Method m = ServerSocketChannel.class.getMethod("open", ProtocolFamily.class);

            return (ServerSocketChannel) m.invoke(null, StandardProtocolFamily.valueOf("UNIX"));
        }
        catch(Exception e) {
            throw new IOException("Cannot open unix domain server socket", e);
        }
    }

    public static SocketChannel openUnixDomainSocketChannel() throws IOException {
        try {
            Method m = SocketChannel.class.getMethod("open", ProtocolFamily.class);

            return (SocketChannel) m.invoke(null, StandardProtocolFamily.valueOf("UNIX"));
        }
        catch(Exception e) {
            throw new IOException("Cannot connect to unix domain server socket", e);
        }
    }

    public static AsynchronousServerSocketChannel openUnixDomainAsynchronousServerSocketChannel() throws IOException {

        try {
            Method m = AsynchronousServerSocketChannel.class.getMethod("open", ProtocolFamily.class);

            return (AsynchronousServerSocketChannel) m.invoke(null, StandardProtocolFamily.valueOf("UNIX"));
        }
        catch(Exception e) {
            throw new IOException("Cannot open unix domain asynchronous server socket", e);
        }
    }

    public static AsynchronousSocketChannel openUnixDomainAsynchronousSocketChannel() throws IOException {

        try {
            Method m = AsynchronousSocketChannel.class.getMethod("open", ProtocolFamily.class);

            return (AsynchronousSocketChannel) m.invoke(null, StandardProtocolFamily.valueOf("UNIX"));
        }
        catch(Exception e) {
            throw new IOException("Cannot open unix domain asynchronous socket", e);
        }
    }
}
