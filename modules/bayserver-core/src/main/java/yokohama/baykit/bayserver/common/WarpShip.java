package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.Warp;
import yokohama.baykit.bayserver.protocol.Command;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Pair;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class WarpShip extends Ship {

    public Warp docker;
    protected Map<Integer, Pair<Integer, Tour>> tourMap = new HashMap<>();

    public SelectableChannel ch;
    public ProtocolHandler protocolHandler;
    boolean connected;
    int socketTimeoutSec;
    ArrayList<Pair<Command, DataConsumeListener>> cmdBuf = new ArrayList<>();

    @Override
    public String toString() {
        return "agt#" + agentId + " wsip#" + shipId + "/" + objectId +
                (protocolHandler != null ? ("[" + protocolHandler.protocol() + "]") : "");
    }


    /////////////////////////////////////
    // Initialize method
    /////////////////////////////////////
    public void initWarp(
            SocketChannel ch,
            int agentId,
            Postman pm,
            Valve vlv,
            Warp dkr,
            ProtocolHandler protoHandler) {
        init(agentId, pm, vlv);
        this.ch = ch;
        this.docker = dkr;
        this.socketTimeoutSec = dkr.timeoutSec() >= 0 ? dkr.timeoutSec() : BayServer.harbor.socketTimeoutSec();
        this.protocolHandler = protoHandler;
        protoHandler.init(this);
    }


    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        if(!tourMap.isEmpty())
            BayLog.error("BUG: Some tours is active: %s", tourMap);
        tourMap.clear();
        connected = false;
        cmdBuf.clear();
        ch = null;
        protocolHandler = null;
    }

    /////////////////////////////////////
    // Implements Ship
    /////////////////////////////////////
    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {

        ((WarpHandler)protocolHandler).verifyProtocol(protocol);

        //  Send pending packet
        GrandAgent agt = GrandAgent.get(agentId);
        agt.multiplexer.reqWrite(ch);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        BayLog.debug("%s notifyConnect", this);
        connected = true;
        for(Pair<Integer, Tour> pir: tourMap.values()) {
            Tour tur = pir.b;
            tur.checkTourId(pir.a);
            WarpData.get(tur).start();
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) throws IOException {
        return protocolHandler.bytesReceived(buf);
    }

    @Override
    public NextSocketAction notifyEof() {
        BayLog.debug("%s EOF detected", this);

        if(tourMap.isEmpty()) {
            BayLog.debug("%s No warp tour. only close", this);
            return NextSocketAction.Close;
        }
        for(int warpId: tourMap.keySet()) {
            Pair<Integer, Tour> pir = tourMap.get(warpId);
            Tour tur = pir.b;
            tur.checkTourId(pir.a);

            try {
                if (!tur.res.headerSent()) {
                    BayLog.debug("%s Send ServiceUnavailable: tur=%s", this, tur);
                    tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "Server closed on reading headers");
                }
                else {
                    // NOT treat EOF as Error
                    BayLog.debug("%s EOF is not an error: tur=%s", this, tur);
                    tur.res.endContent(Tour.TOUR_ID_NOCHECK);
                }
            }
            catch(IOException e) {
                BayLog.debug(e);
            }
        }
        tourMap.clear();

        return NextSocketAction.Close;
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) {

        BayLog.error(e);
        notifyErrorToOwnerTour(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        return true;
    }

    @Override
    public boolean checkTimeout(int durationSec) {

        if(isTimeout(durationSec)) {
            notifyErrorToOwnerTour(HttpStatus.GATEWAY_TIMEOUT, this + " server timeout");
            return true;
        }
        else
            return false;
    }

    @Override
    public void notifyClose() {
        BayLog.debug(this + " notifyClose");
        notifyErrorToOwnerTour(HttpStatus.SERVICE_UNAVAILABLE, this + " server closed");
        endShip();
    }


    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////
    public Warp docker() {
        return docker;
    }

    public WarpHandler warpHandler() {
        return (WarpHandler) protocolHandler;
    }

    public void startWarpTour(Tour tur) throws IOException {
        WarpHandler wHnd = warpHandler();
        int warpId = wHnd.nextWarpId();
        WarpData wdat = wHnd.newWarpData(warpId);
        BayLog.debug("%s new warp tour related to %s", wdat, tur);
        tur.req.setContentHandler(wdat);

        BayLog.debug("%s start: warpId=%d", wdat, warpId);
        if(tourMap.containsKey(warpId))
            throw new Sink("warpId exists");

        tourMap.put(warpId, new Pair<>(tur.id(), tur));
        wHnd.sendHeaders(tur);

        if(connected) {
            BayLog.debug("%s is already connected. Start warp tour:%s", wdat, tur);
            wdat.start();
        }
    }

    public void endWarpTour(Tour tur) throws IOException {
        WarpData wdat = WarpData.get(tur);
        BayLog.debug("%s end: started=%b ended=%b", tur, wdat.started, wdat.ended);
        tourMap.remove(wdat.warpId);
        BayLog.debug("%s keep warp ship", this);
        docker.onEndTour(this);
    }

    public void notifyServiceUnavailable(String msg) throws IOException {
        notifyErrorToOwnerTour(HttpStatus.SERVICE_UNAVAILABLE, msg);
    }

    public Tour getTour(int warpId, boolean must) {
        Pair<Integer, Tour> pair= tourMap.get(warpId);
        if(pair != null) {
            Tour tur = pair.b;
            tur.checkTourId(pair.a);
            if (!WarpData.get(tur).ended) {
                return tur;
            }
        }

        if(must)
            throw new Sink("%s warp tours not found: id=%d", this, warpId);
        else
            return null;
    }

    public Tour getTour(int warpId) {
        return getTour(warpId, true);
    }

    void notifyErrorToOwnerTour(int status, String msg) {
        synchronized (tourMap) {
            tourMap.keySet().forEach(warpId -> {
                Tour tur = getTour(warpId);
                BayLog.debug("%s send error to owner: %s running=%b", this, tur, tur.isRunning());
                try {
                    if (tur.isRunning()) {
                        tur.res.sendError(Tour.TOUR_ID_NOCHECK, status, msg);
                    }
                    else {
                        tur.res.endContent(Tour.TOUR_ID_NOCHECK);
                    }
                } catch (IOException e) {
                    BayLog.error(e);
                }
            });
            tourMap.clear();
        }
    }

    void endShip() {
        docker.onEndShip(this);
    }

    public void abort(int checkId) {
        checkShipId(checkId);
        postman.abort();
    }

    public final boolean isTimeout(long duration) {
        boolean timeout;
        if(keeping) {
            // warp connection never timeout in keeping
            timeout = false;
        }
        else if (socketTimeoutSec <= 0)
            timeout = false;
        else
            timeout = duration >= socketTimeoutSec;

        BayLog.debug(this + " Warp check timeout: dur=" + duration + ", timeout=" + timeout + ", keeping=" + keeping + " limit=" + socketTimeoutSec);
        return timeout;
    }


    public void post(Command cmd) throws IOException {
        post(cmd, null);
    }

    public void post(Command cmd, DataConsumeListener listener) throws IOException {
        if(!connected) {
            Pair<Command, DataConsumeListener> p = new Pair<>(cmd, listener);
            cmdBuf.add(p);
        }
        else {
            protocolHandler.post(cmd, listener);
        }
    }

    public void flush() throws IOException {
        for(Pair<Command, DataConsumeListener> cmdAndLis: cmdBuf) {
            protocolHandler.post(cmdAndLis.a, cmdAndLis.b);
        }
        cmdBuf.clear();
    }
}
