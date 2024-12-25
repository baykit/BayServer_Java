package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.Headers;

import java.io.IOException;

public class WarpData implements ReqContentHandler {

    public final WarpShip warpShip;
    public final int warpShipId;
    public final int warpId;
    public final Headers reqHeaders = new Headers();
    public final Headers resHeaders = new Headers();
    public boolean started;
    public boolean ended;

    public WarpData(WarpShip warpShip, int warpId) {
        this.warpShip = warpShip;
        this.warpShipId = warpShip.id();
        this.warpId = warpId;
        //BayLog.debug("New WarpTour " + warpShip + " warpId#" + warpId + " fromTour " + tour);
    }

    @Override
    public String toString() {
        return warpShip + " wtur#" + warpId;
    }

    //////////////////////////////////////////////////////
    // Implements ReqContentHandler
    //////////////////////////////////////////////////////

    @Override
    public synchronized final void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s onReadReqContent tur=%s len=%d", warpShip, tur, len);
        warpShip.checkShipId(warpShipId);
        int maxLen = warpShip.protocolHandler.maxReqPacketDataSize();
        for(int pos = 0; pos < len; pos += maxLen) {
            int postLen = len - pos;
            if(postLen > maxLen) {
                postLen = maxLen;
            }
            int turId = tur.id();

            if(!started)
                // The buffer will become corrupted due to reuse.
                buf = buf.clone();

            warpShip.warpHandler().sendReqContent(
                    tur,
                    buf,
                    start + pos,
                    postLen,
                    () -> tur.req.consumed(turId, len, lis));
        }
    }

    @Override
    public synchronized final void onEndReqContent(Tour tur) throws IOException {
        BayLog.debug("%s endReqContent tur=%s", warpShip, tur);
        warpShip.checkShipId(warpShipId);
        warpShip.warpHandler().sendEndReq(tur, false, () -> {
            GrandAgent agt = GrandAgent.get(warpShip.agentId);
            agt.netMultiplexer.reqRead(warpShip.rudder);
        });
    }

    @Override
    public synchronized final boolean onAbortReq(Tour tur) {
        BayLog.debug("%s onAbortReq tur=%s", warpShip, tur);
        warpShip.checkShipId(warpShipId);
        warpShip.abort(warpShipId);
        return false; // not aborted immediately
    }

    //////////////////////////////////////////////////////
    // Other methods
    //////////////////////////////////////////////////////

    public final void start() throws IOException {
        if(!started) {
            BayLog.debug("%s Start Warp tour", this);
            warpShip.flush();
            started = true;
        }
    }

    public static WarpData get(Tour tur) {
        return (WarpData)tur.req.contentHandler;
    }
}
