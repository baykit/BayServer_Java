package yokohama.baykit.bayserver.ship;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.util.Counter;
import yokohama.baykit.bayserver.common.Postman;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.common.Valve;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;

/**
 * Ship wraps TCP or UDP connection
 */
public abstract class Ship implements Reusable {

    public static final int SHIP_ID_NOCHECK = -1;
    public static final int INVALID_SHIP_ID = 0;

    static Counter oidCounter = new Counter();
    static Counter idCounter = new Counter();

    public final int objectId;
    public int shipId;
    public int agentId;
    public Postman postman;
    public Valve valve;
    public SelectableChannel ch;
    public ProtocolHandler protocolHandler;
    public boolean initialized;
    public boolean keeping;

    protected Ship() {
        this.objectId = oidCounter.next();
        this.shipId = INVALID_SHIP_ID;
    }

    /////////////////////////////////////
    // Initialize mthods
    /////////////////////////////////////

    protected void init(SelectableChannel ch, int agentId, Postman pm, Valve vlv){
        if(initialized)
            throw new Sink("Ship already initialized");
        this.shipId = idCounter.next();
        this.agentId = agentId;
        this.postman = pm;
        this.valve = vlv;
        this.ch = ch;
        this.initialized = true;
        BayLog.debug("%s Initialized", this);
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        BayLog.debug("%s reset", this);
        initialized = false;
        if(postman != null)
            postman.reset();
        postman = null;  // for reloading certification
        protocolHandler = null;
        agentId = -1;
        shipId = INVALID_SHIP_ID;
        ch = null;
        keeping = false;
    }

    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////

    public void setProtocolHandler(ProtocolHandler protoHandler) {
        this.protocolHandler = protoHandler;
        protoHandler.ship = this;
        BayLog.debug("%s protocol handler is set", this);
    }

    public final int id() {
        return shipId;
    }

    public String protocol() {
        return protocolHandler == null ? "unknown" : protocolHandler.protocol();
    }

    public void resume(int checkId) {
        checkShipId(checkId);
        valve.openValve();
    }

    public final void checkShipId(int shipId) {
        if(!initialized) {
            throw new Sink(this + " Uninitialized ship (might be returned ship): " + shipId);
        }
        if(shipId == 0 || (shipId != SHIP_ID_NOCHECK && shipId != this.shipId)) {
            throw new Sink(this + " Invalid ship id (might be returned ship): " + shipId);
        }
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////

    public abstract NextSocketAction notifyHandshakeDone(String pcl) throws IOException;
    public abstract NextSocketAction notifyConnect() throws IOException;
    public abstract NextSocketAction notifyRead(ByteBuffer buf) throws IOException;
    public abstract NextSocketAction notifyEof();
    public abstract boolean notifyProtocolError(ProtocolException e) throws IOException;
    public abstract void notifyClose();
    public abstract boolean checkTimeout(int durationSec);
}
