package yokohama.baykit.bayserver.rudder;

import yokohama.baykit.bayserver.util.uring.NativeFd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A Rudder implementation wrapping a raw Linux file descriptor.
 * Used by RoverMultiplexer for connections accepted via io_uring.
 *
 * All I/O operations are performed via NativeFd (in bayserver-uring module).
 * This class does NOT import java.lang.foreign directly.
 */
public class NetworkFdRudder extends RudderBase implements NetworkRudder {

    private final int fd;

    // For multiplexer state management (same pattern as ChannelRudder.state)
    public Object state;

    public NetworkFdRudder(int fd) {
        if (fd < 0)
            throw new IllegalArgumentException("Invalid fd: " + fd);
        this.fd = fd;
    }

    public int fd() {
        return fd;
    }

    @Override
    public String toString() {
        return "NetworkFdRudder(fd=" + fd + ")";
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public Object key() {
        return fd;
    }

    @Override
    public void setNonBlocking() throws IOException {
        NativeFd.setNonBlocking(fd);
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        return NativeFd.readToByteBuffer(fd, buf);
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        return NativeFd.writeFromByteBuffer(fd, buf);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            NativeFd.close(fd);
            super.close();
        }
    }

    ////////////////////////////////////////////
    // Network address methods
    ////////////////////////////////////////////

    public int getRemotePort() throws IOException {
        InetSocketAddress addr = NativeFd.getRemoteAddress(fd);
        return addr.getPort();
    }

    public InetAddress getRemoteAddress() throws IOException {
        InetSocketAddress addr = NativeFd.getRemoteAddress(fd);
        return addr.getAddress();
    }

    public InetAddress getLocalAddress() throws IOException {
        InetSocketAddress addr = NativeFd.getLocalAddress(fd);
        return addr.getAddress();
    }

    public int getSocketReceiveBufferSize() throws IOException {
        return 65536;
    }

    ////////////////////////////////////////////
    // Static utility
    ////////////////////////////////////////////

    public static int getFd(Rudder rd) {
        return ((NetworkFdRudder) rd).fd;
    }

    /**
     * Close a raw fd without creating a NetworkFdRudder instance.
     */
    public static void closeFd(int fd) throws IOException {
        NativeFd.close(fd);
    }
}