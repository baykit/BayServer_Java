package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Interface for Rudders that provide network address information.
 * Implemented by both NetworkChannelRudder (NIO-based) and NetworkFdRudder (io_uring-based).
 */
public interface NetworkRudder {
    int getRemotePort() throws IOException;
    InetAddress getRemoteAddress() throws IOException;
    InetAddress getLocalAddress() throws IOException;
    int getSocketReceiveBufferSize() throws IOException;
}