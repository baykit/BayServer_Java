package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.ReadOnlyShip;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Valve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class SendFileShip extends ReadOnlyShip {

    int fileWroteLen;

    Tour tour;
    int tourId;

    public SendFileShip() {
        reset();
    }

    public void init(InputStream input, Tour tur, Valve vlv) throws IOException {
        super.init(input, tur.ship.agent, vlv);
        this.tour = tur;
        this.tourId = tur.tourId;
    }

    @Override
    public String toString() {
        return agent + " fsip#" + shipId + "/" + objectId;
    }


    ////////////////////////////////////////////////////////////////////
    // Implements DataListener
    ////////////////////////////////////////////////////////////////////

    public void reset() {
        super.reset();
        fileWroteLen = 0;
        tourId = 0;
        tour = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements ReadOnlyShip
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {

        fileWroteLen += buf.limit();
        BayLog.debug("%s read file %d bytes: total=%d", this, buf.limit(), fileWroteLen);
        boolean available = tour.res.sendContent(tourId, buf.array(), 0, buf.limit());

        if(available) {
            return NextSocketAction.Continue;
        }
        else {
            return NextSocketAction.Suspend;
        }
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.debug(e);
        try {
            tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, null, e);
        }
        catch(IOException ex) {
            BayLog.debug(ex);
        }
    }

    @Override
    public NextSocketAction notifyEof() {
        try {
            tour.res.endContent(tourId);
        }
        catch(IOException e) {
            BayLog.debug(e);
        }
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        throw new Sink();
    }

}
