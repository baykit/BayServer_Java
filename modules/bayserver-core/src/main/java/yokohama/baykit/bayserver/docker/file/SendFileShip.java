package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.common.ReadOnlyShip;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SendFileShip extends ReadOnlyShip {

    int fileWroteLen;

    FileContent fileContent;
    Tour tour;
    int tourId;

    public void init(Rudder rd, Transporter tp, Tour tur, FileContent fileContent) {
        super.init(tur.ship.agentId, rd, tp);
        this.tour = tur;
        this.tourId = tur.tourId;
        this.fileContent = fileContent;
    }

    @Override
    public String toString() {
        return "agt#" + agentId + " send_file#" + shipId + "/" + objectId;
    }


    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
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
    public NextSocketAction notifyRead(ByteBuffer buf) {

        fileWroteLen += buf.limit();
        BayLog.debug("%s read file %d bytes: total=%d", this, buf.limit(), fileWroteLen);

        try {
            boolean available = tour.res.sendResContent(tourId, buf.array(), 0, buf.limit());

            if(fileContent != null) {
		BayLog.debug("buf=%s target=%s", buf, fileContent.content);
                fileContent.content.put(buf.array(), 0, buf.limit());
            }

            buf.position(buf.limit());

            if(available) {
                return NextSocketAction.Continue;
            }
            else {
                return NextSocketAction.Suspend;
            }
        }
        catch(IOException e) {
            notifyError(e);
            return NextSocketAction.Close;
        }
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
        BayLog.debug("%s EOF", this);

        if(fileContent != null) {
            fileContent.complete();
        }

        try {
            tour.res.endResContent(tourId);
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
        return false;
    }

}
