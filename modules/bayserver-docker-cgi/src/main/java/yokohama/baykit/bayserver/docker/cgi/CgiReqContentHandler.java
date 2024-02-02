package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.util.Map;

public class CgiReqContentHandler implements ReqContentHandler {

    final CgiDocker cgiDocker;
    final Tour tour;
    final int tourId;
    boolean available;
    Process process;
    boolean stdOutClosed;
    boolean stdErrClosed;
    long lastAccess;


    public CgiReqContentHandler(CgiDocker cgiDocker, Tour tur) {
        this.tour = tur;
        this.tourId = tur.id();
        this.cgiDocker = cgiDocker;
    }

    ///////////////////////////////////////////////////////////////////
    // implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s CGI:onReadReqContent: len=%d", tur, len);
        process.getOutputStream().write(buf, start, len);
        process.getOutputStream().flush();
        tur.req.consumed(Tour.TOUR_ID_NOCHECK, len, lis);
        access();
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s CGI:endReqContent", tur);
        access();
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s CGI:abortReq", tur);
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            BayLog.error(e);
        }
        return false;  // not aborted immediately
    }


    ///////////////////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////////////////

    public void startTour(Map<String, String> env) throws HttpException {
        available = false;

        try {
            process = cgiDocker.createProcess(env);
            BayLog.debug("%s created process; %s", tour, process);
        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot create process");
        }

        stdOutClosed = false;
        stdErrClosed = false;
        access();
    }

    public void closePipes() {
        try {
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    public void stdOutClosed() {
        stdOutClosed = true;
        if(stdOutClosed && stdErrClosed)
            processFinished();
    }

    public void stdErrClosed() {
        stdErrClosed = true;
        if(stdOutClosed && stdErrClosed)
            processFinished();
    }

    public void access() {
        lastAccess = System.currentTimeMillis();
    }

    public boolean timedOut() {
        if(cgiDocker.timeoutSec <= 0)
            return false;

        long durationSec = (System.currentTimeMillis() - lastAccess) / 1000;
        BayLog.debug("%s Check CGI timeout: dur=%d, timeout=%d", tour, durationSec, cgiDocker.timeoutSec);
        return durationSec > cgiDocker.timeoutSec;
    }

    private void processFinished() {
        BayLog.debug("%s process_finished()", tour);

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            BayLog.error(e);
        }

        try {

            process.destroy();
            BayLog.trace(tour + " CGITask: process ended");

            int code = process.exitValue();
            BayLog.debug("CGI Process finished: code=%d", code);

            if (code != 0) {
                // Exec failed
                BayLog.error("CGI Exec error code=%d", code & 0xff);
                tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, "Exec failed");
            } else {
                tour.res.endResContent(tourId);
            }
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }
}
