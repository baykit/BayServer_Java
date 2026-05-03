package yokohama.baykit.bayserver.ship;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.Counter;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    public Rudder rudder;
    public Transporter transporter;
    public boolean initialized;
    public boolean keeping;

    protected Ship() {
        this.objectId = oidCounter.next();
        this.shipId = INVALID_SHIP_ID;
    }

    /////////////////////////////////////
    // Initialize mthods
    /////////////////////////////////////

    protected void init(int agentId, Rudder rd, Transporter tp){
        if(initialized)
            throw new Sink("Ship already initialized");
        this.shipId = idCounter.next();
        this.agentId = agentId;
        this.rudder = rd;
        this.transporter = tp;
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
        transporter = null;
        rudder = null;
        agentId = -1;
        shipId = INVALID_SHIP_ID;
        keeping = false;
    }

    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////

    public final int id() {
        return shipId;
    }

    /**
     * Returns whether the multiplexer's internal write buffer for this ship
     * still has room. Delegates to the underlying RudderState.
     */
    public boolean bufferAvailable() {
        if (!(rudder instanceof ChannelRudder))
            return true;
        RudderState st = (RudderState) ((ChannelRudder) rudder).state;
        return st == null || st.bufferAvailable();
    }

    public final void checkShipId(int shipId) {
        if(!initialized) {
            throw new Sink(this + " Uninitialized ship (might be returned ship): " + shipId);
        }
        if(shipId == 0 || (shipId != SHIP_ID_NOCHECK && shipId != this.shipId)) {
            throw new Sink(this + " Invalid ship id (might be returned ship): " + shipId);
        }
    }

    public void resumeRead(int chkId) {
        // STABILITY FIX: H2 warp multiplexes many tours over one ship: when
        // one tour's backend connection ends and the ship is returned to
        // the store, sibling tours may still have deferred consume callbacks
        // pending. Those callbacks fire wsip.resumeRead(sid) with an id the
        // ship has since reset. Crashing the agent is the wrong call --
        // there is no read side to resume on a recycled ship, so just drop.
        if (!initialized || (chkId != SHIP_ID_NOCHECK && chkId != shipId)) {
            BayLog.debug("%s resumeRead on stale ship (chkId=%d, current=%d): ignored",
                    this, chkId, shipId);
            return;
        }
        BayLog.debug("%s resume read", this);
        transporter.reqRead(rudder);
    }

    public void postClose() {
        transporter.reqClose(rudder);
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////

    public abstract NextSocketAction notifyHandshakeDone(String pcl) throws IOException;
    public abstract NextSocketAction notifyConnect() throws IOException;
    public abstract NextSocketAction notifyRead(ByteBuffer buf) throws IOException;
    public abstract NextSocketAction notifyEof();
    public abstract void notifyError(Throwable e);
    public abstract boolean notifyProtocolError(ProtocolException e) throws IOException;
    public abstract void notifyClose();
    public abstract boolean checkTimeout(int durationSec);
}
