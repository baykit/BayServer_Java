package baykit.bayserver.docker.cgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.tour.ReqContentHandler;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.util.HttpStatus;

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


    public CgiReqContentHandler(CgiDocker cgiDocker, Tour tur) {
        this.tour = tur;
        this.tourId = tur.id();
        this.cgiDocker = cgiDocker;
    }

    ///////////////////////////////////////////////////////////////////
    // implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException {
        BayLog.debug("%s CGI:onReadReqContent: len=%d", tur, len);
        process.getOutputStream().write(buf, start, len);
        process.getOutputStream().flush();
        tur.req.consumed(Tour.TOUR_ID_NOCHECK, len);
    }

    @Override
    public void onEndContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s CGI:endReqContent", tur);
    }

    @Override
    public boolean onAbort(Tour tur) {
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
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e, "Cannot create process");
        }

        stdOutClosed = false;
        stdErrClosed = false;
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

    private void processFinished() {

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
                tour.res.endContent(tourId);
            }
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }
}
