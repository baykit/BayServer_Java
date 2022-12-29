package baykit.bayserver.docker.cgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.Sink;
import baykit.bayserver.HttpException;
import baykit.bayserver.train.Train;
import baykit.bayserver.tour.ReqContentHandler;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.train.TrainRunner;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.HttpUtil;
import baykit.bayserver.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.StringTokenizer;

public class CgiTrain extends Train implements ReqContentHandler {

    static final int COMMUNICATE_BUF_SIZE = 16384;

    final CgiDocker cgiDocker;
    Map<String, String> env;
    boolean available;
    Process process;

    public CgiTrain(Tour tur, CgiDocker cgiDocker) {
        super(tur);
        this.cgiDocker = cgiDocker;
    }

    public void startTour(Map<String, String> env) throws HttpException {
        this.env = env;
        try {
            process = cgiDocker.createProcess(env);
        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot create process");
        }
        tour.req.setContentHandler(this);
    }

    ///////////////////////////////////////////////////////////////////
    // implements Runnable
    ///////////////////////////////////////////////////////////////////

    @Override
    public void depart() throws HttpException {

        try {

            // Handle StdOut
            try (InputStream inOut = process.getInputStream()) {

                HttpUtil.parseMessageHeaders(inOut, tour.res.headers);
                if(BayServer.harbor.traceHeader()) {
                    for(String name : tour.res.headers.headerNames()) {
                        for(String value : tour.res.headers.headerValues(name)) {
                            BayLog.info("%s CGI: resHeader: %s=%s", tour, name, value);
                        }
                    }
                }
                String status = tour.res.headers.get("Status");
                if (!StringUtil.empty(status)) {
                    StringTokenizer st = new StringTokenizer(status);
                    String code = st.nextToken();
                    int stCode = Integer.parseInt(code);
                    tour.res.headers.setStatus(stCode);
                }

                tour.res.setConsumeListener((len, resume) -> {
                    if(resume)
                        available = true;
                });

                tour.res.sendHeaders(tourId);

                while (true) {
                    byte[] buf = new byte[COMMUNICATE_BUF_SIZE];
                    int c = inOut.read(buf);
                    if (c == -1)
                        break;

                    BayLog.trace("%s CGITask: read stdout bytes: len=%d", tour, c);
                    available = tour.res.sendContent(tourId, buf, 0, c);

                    try {
                        while(!available) {
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        throw new Sink(e.getMessage());
                    }
                }
            }

            // Handle StdErr
            try (InputStream inErr = process.getErrorStream()) {
                while (true) {
                    byte[] buf = new byte[1024];
                    int c = inErr.read(buf);
                    if (c == -1)
                        break;
                    BayLog.trace("%s CGITask: read stderr bytes: %d", tour, c);
                    System.err.write(buf, 0, c);
                }
            }

            tour.res.endContent(tourId);

        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                BayLog.error(e);
            }

            process.destroy();
            BayLog.trace(tour + " CGITask: process ended");
        }
    }

    ///////////////////////////////////////////////////////////////////
    // implements Tour.ExtraData
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException {
        BayLog.trace("%s CGITask:onReadContent: len=%d", tur, len);
        process.getOutputStream().write(buf, start, len);
        tur.req.consumed(Tour.TOUR_ID_NOCHECK, len);
    }

    @Override
    public void onEndContent(Tour tur) throws IOException, HttpException {
        BayLog.trace("%s CGITask:endContent", tur);
        process.getOutputStream().close();

        if(!TrainRunner.post(this)) {
            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "TourAgents is busy");
        }
    }

    @Override
    public boolean onAbort(Tour tur) {
        BayLog.trace("%s CGITask:abort", tur);
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            BayLog.error(e);
        }
        return false;  // not aborted immediately
    }
}
