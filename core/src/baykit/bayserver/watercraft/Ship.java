package baykit.bayserver.watercraft;

import baykit.bayserver.BayLog;
import baykit.bayserver.Sink;
import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.protocol.ProtocolHandler;
import baykit.bayserver.util.Counter;
import baykit.bayserver.util.Postman;
import baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

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
    public GrandAgent agent;
    public Postman postman;
    public SelectableChannel ch;
    public ProtocolHandler protocolHandler;
    public boolean initialized;
    public boolean keeping;

    protected Ship() {
        this.objectId = oidCounter.next();
        this.shipId = INVALID_SHIP_ID;
    }

    public void init(SelectableChannel ch, GrandAgent agent, Postman pm){
        if(initialized)
            throw new Sink("Ship already initialized");
        this.shipId = idCounter.next();
        this.agent = agent;
        this.postman = pm;
        this.ch = ch;
        this.initialized = true;
        BayLog.debug("%s Initialized", this);
    }

    @Override
    public void reset() {
        BayLog.debug("%s reset", this);
        initialized = false;
        postman.reset();
        postman = null;  // for reloading certification
        protocolHandler = null;
        agent = null;
        shipId = INVALID_SHIP_ID;
        ch = null;
        keeping = false;
    }

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
        postman.openValve();
    }

    public final void checkShipId(int shipId) {
        if(!initialized) {
            throw new Sink(this + " Uninitialized ship (might be returned ship): " + shipId);
        }
        if(shipId == 0 || (shipId != SHIP_ID_NOCHECK && shipId != this.shipId)) {
            throw new Sink(this + " Invalid ship id (might be returned ship): " + shipId);
        }
    }

}
