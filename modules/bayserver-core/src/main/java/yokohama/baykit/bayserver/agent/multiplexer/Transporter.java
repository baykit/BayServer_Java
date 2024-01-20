package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.agent.NextSocketAction;

import java.io.IOException;

public interface Transporter {

    NextSocketAction onConnectable(RudderState st) throws IOException;

    NextSocketAction onReadable(RudderState st) throws IOException;

    NextSocketAction onWritable(RudderState st) throws IOException;

    void onError(RudderState st, Throwable e);

    void onClosed(RudderState st);

    boolean checkTimeout(RudderState st, int durationSec);
}
