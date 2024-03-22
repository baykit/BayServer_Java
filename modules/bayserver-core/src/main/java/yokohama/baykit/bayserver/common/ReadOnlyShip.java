package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;

public abstract class ReadOnlyShip extends Ship {

    protected void init(int agentId, Rudder rd, Transporter tp) {
        super.init(agentId, rd, tp);
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////
    public void reset() {
        super.reset();
    }

    /////////////////////////////////////
    // Implements Ship
    /////////////////////////////////////
    @Override
    public final NextSocketAction notifyHandshakeDone(String pcl) throws IOException {
        throw new Sink();
    }

    @Override
    public final NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public final boolean notifyProtocolError(ProtocolException e) throws IOException {
        BayLog.error(e);
        throw new Sink();
    }
}
