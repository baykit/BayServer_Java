package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public class SelectableChannelRudder extends ChannelRudder{

    public SelectableChannelRudder(SelectableChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public final void setNonBlocking() throws IOException {
        ((SelectableChannel)channel).configureBlocking(false);
    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static SelectableChannel getSelectableChannel(Rudder rd) {
        return (SelectableChannel) ((ChannelRudder)rd).channel;
    }
}
