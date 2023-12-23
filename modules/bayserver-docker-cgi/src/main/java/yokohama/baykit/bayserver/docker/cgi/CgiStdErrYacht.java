package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.watercraft.Yacht;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class CgiStdErrYacht extends Yacht {

    Tour tour;
    int tourId;
    CgiReqContentHandler handler;

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
        this.handler = null;
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

        handler.access();
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyEof() {
        BayLog.debug("%s CGI StdErr: EOF \\(^o^)/", this);
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        BayLog.debug("%s CGI StdErr: notifyClose", this);
        handler.stdErrClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        BayLog.debug("%s CGI StdErr check timeout: dur=%d", tour, durationSec);
        return handler.timedOut();
    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(Tour tur, CgiReqContentHandler handler) {
        super.initYacht();
        this.tour = tur;
        this.tourId = tur.tourId;
        this.handler = handler;
    }
}
