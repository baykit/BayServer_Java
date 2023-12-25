package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.util.Valve;
import yokohama.baykit.bayserver.ship.ReadOnlyShip;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SendFileShip extends ReadOnlyShip {

    int fileWroteLen;

    Tour tour;
    int tourId;

    SendFileShip() {
        reset();
    }

    public void init(GrandAgent agt, Tour tur, Valve tp) throws IOException {
        super.init(agt);
        this.tour = tur;
        this.tourId = tur.tourId;
        tur.res.setConsumeListener((len, resume) -> {
            if(resume) {
                tp.openValve();
            }
        });
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
