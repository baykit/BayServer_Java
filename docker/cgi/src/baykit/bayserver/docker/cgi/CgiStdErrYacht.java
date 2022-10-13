package baykit.bayserver.docker.cgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.Sink;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.watercraft.Yacht;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class CgiStdErrYacht extends Yacht {

    Tour tour;
    int tourId;

    CgiStdErrYacht() {
        reset();
    }

    public String toString() {
        return "CGIErrYat#{" + yachtId + "/" + objectId + " tour=" + tour + " id=" + tourId;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////

    public void reset() {
        tourId = 0;
        tour = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Yacht
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {

        BayLog.debug("%s CGI StdErr %d bytesd", this, buf.limit());
        String msg = new String(buf.array(), 0, buf.limit());
        if(msg.length() > 0)
            BayLog.error("CGI Stderr: %s", msg);

        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s CGI StdErr: EOF\\(^o^)/", this);
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        BayLog.debug("%s CGI StdErr: notifyClose", this);
        ((CgiReqContentHandler) tour.req.contentHandler).stdErrClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        throw new Sink();
    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(Tour tur) {
        super.initYacht();
        this.tour = tur;
        this.tourId = tur.tourId;
    }
}
