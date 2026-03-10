package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.common.ReadOnlyShip;
import yokohama.baykit.bayserver.docker.Barge;
import yokohama.baykit.bayserver.docker.Club;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WaitCargoShip extends ReadOnlyShip {

    Barge.Cargo cargo;
    Club club;
    Tour tour;
    int tourId;

    public void init(Rudder rd, Transporter tp, Tour tur, Barge.Cargo cgo, Club clb) {
        super.init(tur.ship.agentId, rd, tp);
        this.tour = tur;
        this.tourId = tur.tourId;
        this.cargo = cgo;
        this.club = clb;
    }

    @Override
    public String toString() {
        return "agt#" + agentId + " wait_file#" + shipId + "/" + objectId;
    }


    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////

    public void reset() {
        super.reset();
        tourId = 0;
        tour = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements ReadOnlyShip
    ////////////////////////////////////////////////////////////////////

    /**
     * Waked up by pipe
     */
    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) {

        BayLog.debug("%s cargo load completed", tour);

        try {
            if (cargo.exceeded()) {
                BayLog.debug("%s cargo exceeded", tour);
                club.arrive(tour);
            }
            else {
                tour.res.setConsumeListener(ContentConsumeListener.devNull);
                sendCargoOnBoard();
            }
        }
        catch (HttpException e) {
            try {
                tour.res.sendError(Tour.TOUR_ID_NOCHECK, e.status, e.getMessage());
            }
            catch (IOException ex) {
                notifyError(ex);
                return NextSocketAction.Close;
            }
        }

        cargo.releaseRudder(rudder);

        return NextSocketAction.Continue;
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.debug(e, "%s Error notified", tour);
        try {
            tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, null, e);
        }
        catch(IOException ex) {
            BayLog.debug(ex);
        }
    }

    @Override
    public NextSocketAction notifyEof() {
        throw new Sink();
    }

    @Override
    public void notifyClose() {
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        return false;
    }

    private void sendCargoOnBoard() throws HttpException {
        tour.res.setConsumeListener(ContentConsumeListener.devNull);
        cargo.headers().copyTo(tour.res.headers);
        try {
            tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
            tour.res.sendResContent(Tour.TOUR_ID_NOCHECK, cargo.content(), 0, cargo.length());
            tour.res.endResContent(Tour.TOUR_ID_NOCHECK);

        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, cargo.path());
        }
    }

}
