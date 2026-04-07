package yokohama.baykit.bayserver.rudder;

import java.nio.channels.NetworkChannel;

public abstract class NetworkChannelRudder extends ChannelRudder implements NetworkRudder {

    public NetworkChannelRudder(NetworkChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static NetworkChannel getNetworkChannel(Rudder rd) {
        return (NetworkChannel) ((NetworkChannelRudder)rd).channel;
    }
}
