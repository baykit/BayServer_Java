package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public class ReadableByteChannelRudder extends ChannelRudder {

    public ReadableByteChannelRudder(ReadableByteChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {

    }
}
