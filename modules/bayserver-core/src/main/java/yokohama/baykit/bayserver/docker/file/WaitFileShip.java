package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.common.ReadOnlyShip;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WaitFileShip extends ReadOnlyShip {

    FileContent fileContent;
    FileContentHandler handler;

    Tour tour;
    int tourId;

    public void init(Rudder rd, Transporter tp, Tour tur, FileContent fileContent, FileContentHandler handler) {
        super.init(tur.ship.agentId, rd, tp);
        this.tour = tur;
        this.tourId = tur.tourId;
        this.fileContent = fileContent;
        this.handler = handler;
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
        fileContent = null;
        tourId = 0;
        tour = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements ReadOnlyShip
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) {

        BayLog.debug("%s file read completed", this);

        try {
            handler.sendFileFromCache();
        }
        catch(HttpException e) {
            try {
                tour.res.sendError(Tour.TOUR_ID_NOCHECK, e.status, e.getMessage());
            }
            catch(IOException ex) {
                notifyError(ex);
                return NextSocketAction.Close;
            }
        }

        return NextSocketAction.Continue;
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.debug(e, "%s Error notified", this);
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

}
