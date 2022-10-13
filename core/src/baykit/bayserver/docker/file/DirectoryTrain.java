package baykit.bayserver.docker.file;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.Sink;
import baykit.bayserver.tour.ReqContentHandler;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.train.Train;
import baykit.bayserver.train.TrainRunner;
import baykit.bayserver.util.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

public class DirectoryTrain extends Train implements ReqContentHandler {

    final File path;

    boolean available;
    boolean abortable;

    public DirectoryTrain(Tour tur, File path) {
        super(tur);
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
    public void depart() throws HttpException {

        try {
            tour.res.headers.setContentType("text/html");

            tour.res.setConsumeListener((len, resume) -> {
                if(resume)
                    available = true;
            });

            tour.res.sendHeaders(tourId);

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
            available = tour.res.sendContent(tourId, s.getBytes(), 0, s.length());

            try {
                while(!available) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                BayLog.error(e);
                throw new Sink(e.getMessage());
            }

            tour.res.endContent(tourId);


        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // implements Tour.ExtraData
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException {
        BayLog.debug("%s onReadContent(Ignore) len=%d", tur, len);
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
