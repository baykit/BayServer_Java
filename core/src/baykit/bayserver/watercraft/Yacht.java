package baykit.bayserver.watercraft;

import baykit.bayserver.Sink;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.agent.transporter.DataListener;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.util.Counter;
import baykit.bayserver.util.Reusable;

import java.io.IOException;

/**
 * Yacht wraps input stream
 */
public abstract class Yacht implements DataListener, Reusable {

    public static final int INVALID_YACHT_ID = 0;
    static Counter oidCounter = new Counter();
    static Counter idCounter = new Counter();

    public final int objectId;
    public int yachtId;

    protected Yacht() {
        this.objectId = oidCounter.next();
        this.yachtId = INVALID_YACHT_ID;
    }

    public void initYacht() {
        this.yachtId = idCounter.next();
    }

    @Override
    public final NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public final NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
        throw new Sink();
    }

    @Override
    public final boolean notifyProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

}
