package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.Valve;
import yokohama.baykit.bayserver.watercraft.Yacht;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;

public class CgiStdOutYacht extends Yacht {

    int fileWroteLen;

    Tour tour;
    int tourId;
    CgiReqContentHandler handler;

    String remain = "";
    boolean headerReading;

    CgiStdOutYacht() {
        reset();
    }

    public String toString() {
        return "CGIYat#" + yachtId + "/" + objectId + " tour=" + tour + " id=" + tourId;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        fileWroteLen = 0;
        tourId = 0;
        tour = null;
        headerReading = true;
        remain = "";
        handler = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Yacht
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {

        fileWroteLen += buf.limit();
        BayLog.debug("%s read file %s bytes: %d", this, buf.limit(), fileWroteLen);

        if (headerReading) {

            while(true) {
                int pos = -1;
                for(int i = buf.position(); i < buf.limit(); i++) {
                    // ByteArray.get(int) method does not increment position
                    if (buf.get(i) == (byte) '\n') {
                        pos = i;
                        break;
                    }
                }
                //BayLog.debug("%s pos: %d", this, pos);

                if (pos == -1) {
                    break;
                }

                String line = new String(buf.array(), buf.position(), pos - buf.position());
                if(remain.length() > 0) {
                    line = remain + line;
                    remain = "";
                }
                buf.position(pos + 1);

                line = line.trim();
                //BayLog.debug("line: %s", line);

                //  if line is empty ("\r\n")
                //  finish header reading.
                if (StringUtil.empty(line)) {
                    headerReading = false;
                    tour.res.sendHeaders(tourId);
                    break;
                } else {
                    if(BayServer.harbor.traceHeader()) {
                         BayLog.info("%s CGI: res header line: %s", tour, line);
                    }

                    int sepPos = line.indexOf(':');
                    if (sepPos >= 0) {
                        String key = line.substring(0, sepPos).trim();
                        String val = line.substring(sepPos + 1).trim();
                        if (key.equalsIgnoreCase("status")) {
                            try {
                                StringTokenizer st = new StringTokenizer(val);
                                tour.res.headers.setStatus(Integer.parseInt(st.nextToken()));
                            }
                            catch(Exception e) {
                                BayLog.error(e);
                            }
                        }
                        else
                            tour.res.headers.add(key, val);
                    }
                }
            }
        }

        boolean available = true;

        if(headerReading) {
            remain += new String(buf.array(), buf.position(), buf.limit() - buf.position());
        }
        else {
            if(buf.hasRemaining()) {
                available = tour.res.sendContent(tourId, buf.array(), buf.position(), buf.limit() - buf.position());
            }
        }

        handler.access();
        if(available)
            return NextSocketAction.Continue;
        else
            return NextSocketAction.Suspend;
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s CGI StdOut: EOF \\(^o^)/", this);
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        BayLog.debug("%s CGI StdOut: notifyClose", this);
        ((CgiReqContentHandler)tour.req.contentHandler).stdOutClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        BayLog.debug("%s CGI StdOut check timeout: dur=%d", tour, durationSec);

        if (handler.timedOut()) {
            // Kill cgi process instead of handing timeout
            BayLog.warn("%s Kill process!: %s", tour, handler.process);
            handler.process.destroyForcibly();
            return true;
        }
        return false;
    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(Tour tur, Valve vv, CgiReqContentHandler handler) {
        super.initYacht();
        this.handler = handler;
        this.tour = tur;
        this.tourId = tur.tourId;
        tur.res.setConsumeListener((len, resume) -> {
            if(resume) {
                vv.openValve();
            }
        });
    }
}
