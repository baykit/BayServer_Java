package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourStore;
import yokohama.baykit.bayserver.util.Counter;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;


/**
 * Handles TCP/IP connection
 */
public class InboundShip extends Ship {

    protected Port portDkr;

    static Counter errCounter = new Counter();
    boolean needEnd;
    protected int socketTimeoutSec;

    TourStore tourStore;
    List<Tour> activeTours = new ArrayList<>();

    public void initInbound(
            SelectableChannel ch,
            GrandAgent agt,
            Transporter tp,
            Port portDkr,
            ProtocolHandler protoHandler) {
        super.init(ch, agt, tp, tp);
        this.portDkr = portDkr;
        this.socketTimeoutSec = portDkr.timeoutSec() >= 0 ? portDkr.timeoutSec() : BayServer.harbor.socketTimeoutSec();
        this.tourStore = TourStore.getStore(agt.agentId);
        setProtocolHandler(protoHandler);
    }

    @Override
    public String toString() {
        return agent + " ship#" + shipId + "/" + objectId + "[" + protocol() + "]";
    }


    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public synchronized void reset() {
        super.reset();
        synchronized (this) {
            if (!activeTours.isEmpty()) {
                throw new Sink("%s There are some running tours", this);
            }
        }
        needEnd = false;
    }

    /////////////////////////////////////
    // Implements ship
    /////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String pcl) {
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) throws IOException {
        return protocolHandler.bytesReceived(buf);
    }

    @Override
    public NextSocketAction notifyEof() {
        BayLog.debug("%s EOF detected", this);
        return NextSocketAction.Close;
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        if(BayLog.isDebugMode()) {
            BayLog.error(e);
        }
        return protocolHandler.onProtocolError(e);
    }

    @Override
    public synchronized void notifyClose() {
        BayLog.debug("%s notifyClose", this);

        abortTours();

        if(!activeTours.isEmpty()) {
            // cannot close because there are some running tours
            BayLog.debug(this + " cannot end ship because there are some running tours (ignore)");
            needEnd = true;
        }
        else {
            endShip();
        }
    }

    @Override
    public boolean checkTimeout(int durationSec) {
        boolean timeout;
        if(socketTimeoutSec <= 0)
            timeout = false;
        else if(keeping)
            timeout = durationSec >= BayServer.harbor.keepTimeoutSec();
        else
            timeout = durationSec >= socketTimeoutSec;

        BayLog.debug("%s Check timeout: dur=%d, timeout=%b, keeping=%b limit=%d keeplim=%d",
                this, durationSec, timeout, keeping, socketTimeoutSec, BayServer.harbor.keepTimeoutSec());
        return timeout;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////
    public Port portDocker() {
        return portDkr;
    }

    public Tour getTour(int turKey) {
        return getTour(turKey, false);
    }

    public Tour getTour(int turKey, boolean force) {
        return getTour(turKey, force, true);
    }

    public Tour getTour(int turKey, boolean force, boolean rent) {
        synchronized (tourStore) {
            long storeKey = uniqKey(shipId, turKey);
            Tour tur = tourStore.get(storeKey);
            if (tur == null && rent) {
                tur = tourStore.rent(storeKey, force);
                if(tur == null)
                    return null;
                tur.init(turKey, this);
                activeTours.add(tur);
            }
            if(tur.ship != this)
                throw new Sink();
            tur.checkTourId(tur.id());
            return tur;
        }
    }


    public Tour getErrorTour() {
        int turKey = errCounter.next();
        long storeKey = uniqKey(shipId, -turKey);
        Tour tur = tourStore.rent(storeKey,true);
        tur.init(-turKey, this);
        activeTours.add(tur);
        return tur;
    }

    public void sendHeaders(int chkId, Tour tur) throws IOException {
        checkShipId(chkId);

        for(String[] nv: portDkr.additionalHeaders()) {
            tur.res.headers.add(nv[0], nv[1]);
        }
        ((InboundHandler) protocolHandler).sendResHeaders(tur);
    }

    public void sendResContent(int chkId, Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        checkShipId(chkId);

        int maxLen = protocolHandler.maxResPacketDataSize();
        if(len > maxLen) {
            sendResContent(Tour.TOUR_ID_NOCHECK, tur, bytes, ofs, maxLen, null);
            sendResContent(Tour.TOUR_ID_NOCHECK, tur, bytes, ofs + maxLen, len - maxLen, lis);
        }
        else {
            ((InboundHandler) protocolHandler).sendResContent(tur, bytes, ofs, len, lis);
        }
    }

    public void sendEndTour(int chkId, Tour tur, DataConsumeListener lis) throws IOException {
        checkShipId(chkId);

        BayLog.debug("%s sendEndTour: %s state=%s", this, tur, tur.state);

        if(!tur.isValid()) {
            throw new Sink("Tour is not valid");
        }
        boolean keepAlive = false;
        if (tur.req.headers.getConnection() == Headers.ConnectionType.KeepAlive)
            keepAlive = true;
        if(keepAlive) {
            Headers.ConnectionType resConn = tur.res.headers.getConnection();
            keepAlive = (resConn == Headers.ConnectionType.KeepAlive)
                    || (resConn == Headers.ConnectionType.Unknown);
            if (keepAlive) {
                if (tur.res.headers.contentLength() < 0)
                    keepAlive = false;
            }
        }

        ((InboundHandler)protocolHandler).sendEndTour(tur, keepAlive, lis);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Custom methods
    ///////////////////////////////////////////////////////////////////////////////////////////////

    void endShip() {
        BayLog.debug("%s endShip", this);
        portDkr.returnProtocolHandler(agent, protocolHandler);
        portDkr.returnShip(this);
    }

    public void abortTours() {
        ArrayList<Tour> returnList = new ArrayList<>();

        // Abort tours
        for(Tour tur: activeTours) {
            if(tur.isValid()) {
                BayLog.debug("%s is valid, abort it: stat=%s", tur, tur.state);
                if(tur.req.abort()) {
                    returnList.add(tur);
                }
            }
        }

        for(Tour tur: returnList) {
            returnTour(tur);
        }
    }

    private static long uniqKey(int sipId, int turKey) {
        return ((long)sipId) << 32 | turKey;
    }

    public void returnTour(Tour tur) {
        BayLog.debug("%s Return tour: %s", this, tur);

        synchronized (tourStore) {
            if (!activeTours.contains(tur))
                throw new Sink("Tour is not in acive list: %s", tur);
            tourStore.Return(uniqKey(shipId, tur.req.key));
            activeTours.remove(tur);

            if (needEnd && activeTours.isEmpty()) {
                endShip();
            }
        }
    }
}

