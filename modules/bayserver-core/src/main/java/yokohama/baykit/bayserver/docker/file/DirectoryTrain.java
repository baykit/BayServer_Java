package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

public class DirectoryTrain extends Train implements ReqContentHandler {

    final File path;
    Tour tour;
    boolean available;
    boolean abortable;

    public DirectoryTrain(Tour tur, File path) {
        this.tour = tur;
        this.path = path;
        this.abortable = true;
    }

    public void startTour() throws HttpException {
        tour.req.setContentHandler(this);
    }

    ///////////////////////////////////////////////////////////////////
    // implements Train
    ///////////////////////////////////////////////////////////////////

    @Override
    public void depart() {

        try {
            tour.res.headers.setContentType("text/html");

            tour.res.setConsumeListener((len, resume) -> {
                if(resume)
                    available = true;
            });

            tour.res.sendHeaders(tour.tourId);

            StringWriter w = new StringWriter();
            w.write("<html><body><br>");
            File[] files = path.listFiles();
            if(!tour.req.uri.equals("/")) {
                printLink(w, "../");
            }
            if(files != null) {
                for (File f : files) {
                    if(f.isDirectory()) {
                        if(!f.getName().equals(".")) {
                            printLink(w, f.getName() + "/");
                        }
                    }
                    else {
                        printLink(w, f.getName());
                    }
                }
            }
            w.write("</body></html>");
            String s = w.toString();

            BayLog.trace("%s Directory: send contents: len=%d", tour, s.length());
            available = tour.res.sendContent(tour.tourId, s.getBytes(), 0, s.length());

            try {
                while(!available) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                BayLog.error(e);
                throw new Sink(e.getMessage());
            }

            tour.res.endContent(tour.tourId);

        } catch (IOException e) {
            BayLog.error(e);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // implements Tour.ExtraData
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s endContent", tur);
        abortable = false;

        if(!TrainRunner.post(this)) {
            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "TourRunner is busy");
        }
    }

    @Override
    public boolean onAbort(Tour tur) {
        BayLog.debug("%s onAbort aborted=%s", tur, abortable);
        return abortable;
    }


    private void printLink(StringWriter w, String path) {
        w.write("<a href='" + path + "'>");
        w.write(path);
        w.write("</a><br>");
    }
}
