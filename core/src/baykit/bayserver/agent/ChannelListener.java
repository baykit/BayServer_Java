package baykit.bayserver.agent;

import java.io.IOException;
import java.nio.channels.Channel;

public interface ChannelListener {

    NextSocketAction onReadable(Channel ch) throws IOException;

    NextSocketAction onWritable(Channel ch) throws IOException;

    NextSocketAction onConnectable(Channel ch) throws IOException;

    void onError(Channel ch, Throwable e);

    void onClosed(Channel ch);

    boolean checkTimeout(Channel ch, int durationSec);
}
