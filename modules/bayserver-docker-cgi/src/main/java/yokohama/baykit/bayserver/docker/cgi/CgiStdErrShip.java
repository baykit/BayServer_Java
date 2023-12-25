package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.ship.ReadOnlyShip;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CgiStdErrShip extends ReadOnlyShip  {

    CgiReqContentHandler handler;

    CgiStdErrShip() {
        reset();
    }


    @Override
    public String toString() {
        return agent + " err_sip#" + shipId + "/" + objectId;
    }


    /////////////////////////////////////
    // Initialize methods
    /////////////////////////////////////
    public void init(GrandAgent agt, CgiReqContentHandler handler) {
        init(agt);
        this.handler = handler;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    public void reset() {
        super.reset();
        this.handler = null;
    }

    /////////////////////////////////////
    // Implements ReadOnlyShip
    /////////////////////////////////////
    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {

        BayLog.debug("%s CGI StdErr: read %d bytes", this, buf.limit());
        String msg = new String(buf.array(), 0, buf.limit());
        if(msg.length() > 0)
            BayLog.error("CGI Stderr: %s", msg);

        handler.access();
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyEof() {
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        handler.stdErrClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        return handler.timedOut();
    }


}