package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;

import java.io.File;
import java.io.IOException;

public class FileContentHandler implements ReqContentHandler {

    final File path;
    boolean abortable;

    public FileContentHandler(File path) {
        this.path = path;
        this.abortable = true;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onReadContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s endContent", tur);
        tur.res.sendFile(Tour.TOUR_ID_NOCHECK, path, tur.res.charset(), true);
        abortable = false;
    }

    @Override
    public boolean onAbort(Tour tur) {
        BayLog.debug("%s onAbort aborted=%s", tur, abortable);
        return abortable;
    }
}
