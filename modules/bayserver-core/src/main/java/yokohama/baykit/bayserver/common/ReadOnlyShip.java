package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.channels.Channel;

public abstract class ReadOnlyShip extends Ship {

    public Channel channel;

    protected void init(Channel ch, int agentId, Valve vlv) {
        super.init(agentId, null, vlv);
        this.channel = ch;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////
    public void reset() {
        super.reset();
        this.channel = null;
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
        throw new Sink();
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract void notifyError(Throwable e);
}
