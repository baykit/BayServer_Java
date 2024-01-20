package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.RudderState;

import java.io.IOException;

public interface SelectHandler {

    NextSocketAction onConnectable(RudderState st) throws IOException;

    NextSocketAction onReadable(RudderState st) throws IOException;

    NextSocketAction onWritable(RudderState st) throws IOException;

    void onError(RudderState st, Throwable e);

    void onClosed(RudderState st);

    boolean checkTimeout(RudderState st, int durationSec);
}
