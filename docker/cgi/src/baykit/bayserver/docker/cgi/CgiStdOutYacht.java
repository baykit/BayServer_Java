package baykit.bayserver.docker.cgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.Sink;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.util.StringUtil;
import baykit.bayserver.util.Valve;
import baykit.bayserver.watercraft.Yacht;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;

public class CgiStdOutYacht extends Yacht {

    int fileWroteLen;

    Tour tour;
    int tourId;

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

        if(available)
            return NextSocketAction.Continue;
        else
            return NextSocketAction.Suspend;
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s CGI StdOut: EOF(^o^)", this);
        return NextSocketAction.Close;
    }

    @Override
    public void notifyClose() {
        BayLog.debug("%s CGI StdOut: notifyClose", this);
        ((CgiReqContentHandler)tour.req.contentHandler).stdOutClosed();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        throw new Sink();
    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(Tour tur, Valve tp) {
        super.initYacht();
        this.tour = tur;
        this.tourId = tur.tourId;
        tur.res.setConsumeListener((len, resume) -> {
            if(resume) {
                tp.openValve();
            }
        });
    }
}
