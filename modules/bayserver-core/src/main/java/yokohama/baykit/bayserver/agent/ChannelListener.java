package yokohama.baykit.bayserver.agent;

import java.io.IOException;
import java.nio.channels.Channel;

public interface ChannelListener {

    NextSocketAction onReadable(Channel chkCh) throws IOException;

    NextSocketAction onWritable(Channel chkCh) throws IOException;

    NextSocketAction onConnectable(Channel chkCh) throws IOException;

    void onError(Channel chkCh, Throwable e);

    void onClosed(Channel chkCh);

    boolean checkTimeout(Channel chkCh, int durationSec);
}
