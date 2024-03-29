package yokohama.baykit.bayserver.docker.warp;

import yokohama.baykit.bayserver.BayLog;
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
    public synchronized final void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException {
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

            warpShip.warpHandler().postWarpContents(
                    tur,
                    buf,
                    start + pos,
                    postLen,
                    () -> tur.req.consumed(turId, len));
        }
    }

    @Override
    public synchronized final void onEndContent(Tour tur) throws IOException {
        BayLog.debug("%s endReqContent tur=%s", warpShip, tur);
        warpShip.checkShipId(warpShipId);
        warpShip.warpHandler().postWarpEnd(tur);
    }

    @Override
    public synchronized final boolean onAbort(Tour tur) {
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
            warpShip.protocolHandler.commandPacker.flush(warpShip);
            BayLog.debug("%s Start Warp tour", this);
            warpShip.flush();
            started = true;
        }
    }

    public static WarpData get(Tour tur) {
        return (WarpData)tur.req.contentHandler;
    }
}
