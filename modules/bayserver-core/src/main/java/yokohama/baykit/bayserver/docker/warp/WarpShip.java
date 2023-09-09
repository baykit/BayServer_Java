package yokohama.baykit.bayserver.docker.warp;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Pair;
import yokohama.baykit.bayserver.watercraft.Ship;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public final class WarpShip extends Ship {

    public WarpDocker docker;
    protected Map<Integer, Pair<Integer, Tour>> tourMap = new HashMap<>();

    boolean connected;
    int socketTimeoutSec;

    public void initWarp(
            SocketChannel ch,
            GrandAgent agent,
            Transporter tp,
            WarpDocker dkr,
            ProtocolHandler protoHandler) {
        super.init(ch, agent, tp);
        this.docker = dkr;
        this.socketTimeoutSec = docker.timeoutSec >= 0 ? docker.timeoutSec : BayServer.harbor.socketTimeoutSec();
        setProtocolHandler(protoHandler);
    }


    @Override
    public String toString() {
        return agent + " wsip#" + shipId + "/" + objectId + "[" + protocol() + "]";
    }

    //////////////////////////////////////////////////////
    // implements Reusable
    //////////////////////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        if(!tourMap.isEmpty())
            BayLog.error("BUG: Some tours is active: %s", tourMap);
        connected = false;
    }

    //////////////////////////////////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////////////////////////////////
    public WarpDocker docker() {
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
        wHnd.postWarpHeaders(tur);

        if(connected) {
            BayLog.debug("%s is already connected. Start warp tour:%s", wdat, tur);
            wdat.start();
        }
    }

    public void endWarpTour(Tour tur) throws IOException {
        WarpData wdat = WarpData.get(tur);
        BayLog.debug("%s end: started=%b ended=%b", tur, wdat.started, wdat.ended);
        tourMap.remove(wdat.warpId);
        docker.keepShip(this);
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


    //////////////////////////////////////////////////////
    // Other methods
    //////////////////////////////////////////////////////

    protected void notifyErrorToOwnerTour(int status, String msg) {
        synchronized (tourMap) {
            tourMap.keySet().forEach(warpId -> {
                Tour tur = getTour(warpId);
                BayLog.debug("%s send error to owner: %s running=%b", this, tur, tur.isRunning());
                if (tur.isRunning()) {
                    try {
                        tur.res.sendError(Tour.TOUR_ID_NOCHECK, status, msg);
                    } catch (IOException e) {
                        BayLog.error(e);
                    }
                }
            });
            tourMap.clear();
        }
    }

    void endShip() {
        docker.returnProtocolHandler(agent, protocolHandler);
        docker.returnShip(this);
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

}
