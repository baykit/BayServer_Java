package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public abstract class ChannelRudder implements Rudder {
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
    public int read(ByteBuffer buf) throws IOException {
        return ((ReadableByteChannel)channel).read(buf);
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        return ((WritableByteChannel)channel).write(buf);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static Channel getChannel(Rudder rd) {
        return ((ChannelRudder)rd).channel;
    }
}
