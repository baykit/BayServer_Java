package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

public class WritableByteChannelRudder extends ChannelRudder {

    public WritableByteChannelRudder(WritableByteChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {

    }
}
