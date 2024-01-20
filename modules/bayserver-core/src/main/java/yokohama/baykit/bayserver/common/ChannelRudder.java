package yokohama.baykit.bayserver.common;

import java.io.IOException;
import java.nio.channels.Channel;

public class ChannelRudder implements Rudder{
    public final Channel channel;

    public ChannelRudder(Channel channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return channel.toString();
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public Object key() {
        return channel;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
