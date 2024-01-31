package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;

public class AsynchronousFileChannelRudder extends ChannelRudder {

    public AsynchronousFileChannelRudder(AsynchronousFileChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {

    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static AsynchronousFileChannel getAsynchronousFileChannel(Rudder rd) {
        return (AsynchronousFileChannel) ((ChannelRudder)rd).channel;
    }
}
