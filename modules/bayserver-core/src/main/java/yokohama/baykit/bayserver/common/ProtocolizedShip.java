package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.ship.Ship;

import java.nio.channels.SelectableChannel;

public abstract class ProtocolizedShip extends Ship {

    public SelectableChannel ch;
    public ProtocolHandler protocolHandler;

    protected void initProtocolized(
            SelectableChannel ch,
            int agentId,
            Transporter tp,
            ProtocolHandler protoHandler
    ) {
        super.init(agentId, tp, tp);
        this.ch = ch;
        this.protocolHandler = protoHandler;
        setProtocolHandler(protoHandler);
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public synchronized void reset() {
        super.reset();
        ch = null;
        protocolHandler = null;
    }

    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////

    public final void setProtocolHandler(ProtocolHandler protoHandler) {
        this.protocolHandler = protoHandler;
        protoHandler.ship = this;
        BayLog.debug("%s protocol handler is set", this);
    }
}
