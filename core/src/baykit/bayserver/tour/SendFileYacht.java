package baykit.bayserver.tour;

import baykit.bayserver.BayLog;
import baykit.bayserver.Sink;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.util.Valve;
import baykit.bayserver.watercraft.Yacht;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class SendFileYacht extends Yacht {

    File file;
    int fileLen;
    int fileWroteLen;

    Tour tour;
    int tourId;

    SendFileYacht() {
        reset();
    }

    public String toString() {
        return "fyacht#" + yachtId + "/" + objectId + " tour=" + tour + " id=" + tourId;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////

    public void reset() {
        fileWroteLen = 0;
        tourId = 0;
        fileLen = 0;
        tour = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Yacht
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {

        fileWroteLen += buf.limit();
        BayLog.debug("%s read file %d bytes: total=%d/%d", this, buf.limit(), fileWroteLen, fileLen);
        boolean available = tour.res.sendContent(tourId, buf.array(), 0, buf.limit());

        if(available) {
            return NextSocketAction.Continue;
        }
        else {
            return NextSocketAction.Suspend;
        }

    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s EOF(^o^) %s", this, file.getPath());
        tour.res.endContent(tourId);
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        BayLog.debug("File closed: %s", file.getPath());
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        throw new Sink();
    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(Tour tur, File file, Valve tp) throws IOException{
        super.initYacht();
        this.tour = tur;
        this.tourId = tur.tourId;
        this.file = file;
        this.fileLen = (int)file.length();
        tur.res.setConsumeListener((len, resume) -> {
            if(resume) {
                tp.openValve();
            }
        });
    }
}
