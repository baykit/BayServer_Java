package yokohama.baykit.bayserver.docker.warp;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WarpDataListener implements DataListener {

    WarpShip ship;

    public WarpDataListener(WarpShip ship) {
        this.ship = ship;
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    //////////////////////////////////////////////////////
    // Implements DataListener
    //////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {

        ((WarpHandler)ship.protocolHandler).verifyProtocol(protocol);

        //  Send pending packet
        ship.agent.nonBlockingHandler.askToWrite(ship.ch);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        BayLog.debug("%s notifyConnect", this);
        ship.connected = true;
        for(Pair<Integer, Tour> pir: ship.tourMap.values()) {
            Tour tur = pir.b;
            tur.checkTourId(pir.a);
            WarpData.get(tur).start();
        }
        return NextSocketAction.Continue;
    }

    @Override
    public final NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        return ship.protocolHandler.bytesReceived(buf);
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s EOF detected", this);

        if(ship.tourMap.isEmpty()) {
            BayLog.debug("%s No warp tour. only close", this);
            return NextSocketAction.Close;
        }
        for(int warpId: ship.tourMap.keySet()) {
            Pair<Integer, Tour> pir = ship.tourMap.get(warpId);
            Tour tur = pir.b;
            tur.checkTourId(pir.a);

            if (!tur.res.headerSent()) {
                BayLog.debug("%s Send ServiceUnavailable: tur=%s", this, tur);
                tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "Server closed on reading headers");
            } else {
                // NOT treat EOF as Error
                BayLog.debug("%s EOF is not an error: tur=%s", this, tur);
                try {
                    tur.res.endContent(Tour.TOUR_ID_NOCHECK);
                }
                catch(IOException e) {
                    BayLog.debug(e, "%s end content error: tur=%s", this, tur);
                }
            }
        }
        ship.tourMap.clear();

        return NextSocketAction.Close;
    }

    @Override
    public final boolean notifyProtocolError(ProtocolException e) {

        BayLog.error(e);
        ship.notifyErrorToOwnerTour(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        return true;
    }

    @Override
    public final boolean checkTimeout(int durationSec) {

        if(ship.isTimeout(durationSec)) {
            ship.notifyErrorToOwnerTour(HttpStatus.GATEWAY_TIMEOUT, this + " server timeout");
            return true;
        }
        else
            return false;
    }

    @Override
    public final void notifyClose() {
        BayLog.debug(this + " notifyClose");
        ship.notifyErrorToOwnerTour(HttpStatus.SERVICE_UNAVAILABLE, this + " server closed");
        ship.endShip();
    }
}
