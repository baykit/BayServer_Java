package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class WriteOnlyShip extends Ship {

    protected void init(GrandAgent agt) {
        super.init(null, agt, null, null);
    }

    /////////////////////////////////////
    // Implements Ship
    /////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String pcl) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyEof() {
        throw new Sink();
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract void notifyClose();
}
