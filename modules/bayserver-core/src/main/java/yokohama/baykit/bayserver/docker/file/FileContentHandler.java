package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.DirectoryException;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

public class FileContentHandler implements ReqContentHandler {

    final Tour tour;
    final Path path;
    final String charset;

    final boolean listFiles;
    boolean abortable;

    public FileContentHandler(
            Tour tur,
            Path path,
            String charset,
            boolean listFiles) {
        this.tour = tur;
        this.path = path;
        this.charset = charset;
        this.abortable = true;
        this.listFiles = listFiles;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s file:onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s file:endContent", tur);
        reqStartTour();
        abortable = false;
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s file:onAbort aborted=%s", tur, abortable);
        return abortable;
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Sending file methods
    ////////////////////////////////////////////////////////////////////////////////

    public synchronized void reqStartTour() throws HttpException {
        BayLog.debug("%s reqStartTour", tour);

         try {
             tour.res.sendFile(path.toString(), charset);
         }
         catch (DirectoryException e) {
             handleDirectory(tour, path);
         }
         catch (FileNotFoundException | NoSuchFileException e) {
            throw new HttpException(HttpStatus.NOT_FOUND, path.toString());
         }
         catch (Exception e) {
             BayLog.error(e);
             throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, path.toString());
         }
    }


    private void handleDirectory(Tour tur, Path path) throws HttpException {
        if(listFiles) {
            DirectoryTrain train = new DirectoryTrain(tur, path);
            train.startTour();
        }
        else {
            throw new HttpException(HttpStatus.FORBIDDEN, "Directory scan is prohibited");
        }
    }
}
