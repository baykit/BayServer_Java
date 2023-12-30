package yokohama.baykit.bayserver.agent;

import java.io.IOException;

public interface ChannelListener<T> {

    NextSocketAction onReadable(T chkCh) throws IOException;

    NextSocketAction onWritable(T chkCh) throws IOException;

    NextSocketAction onConnectable(T chkCh) throws IOException;

    void onError(T chkCh, Throwable e);

    void onClosed(T chkCh);

    boolean checkTimeout(T chkCh, int durationSec);
}
