package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;

public abstract class NetworkChannelRudder extends SelectableChannelRudder {

    public NetworkChannelRudder(NetworkChannel ch) {
        super((SelectableChannel) ch);
    }

    ////////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////////

    public abstract int getRemotePort() throws IOException;
    public abstract InetAddress getRemoteAddress() throws IOException;
    public abstract InetAddress getLocalAddress() throws IOException;
    public abstract int getSocketReceiveBufferSize() throws IOException;

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static NetworkChannel getNetworkChannel(Rudder rd) {
        return (NetworkChannel) ((NetworkChannelRudder)rd).channel;
    }
}
