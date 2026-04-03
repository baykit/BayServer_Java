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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DirectoryTrain extends Train {

    final Path path;
    Tour tour;
    boolean available;
    boolean abortable;

    public DirectoryTrain(Tour tur, Path path) {
        this.tour = tur;
        this.path = path;
        this.abortable = true;
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

            if(!tour.req.uri.equals("/")) {
                printLink(w, "../");
            }
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(f -> {
                    if(Files.isDirectory(f)) {
                        if(!f.getFileName().equals(".")) {
                            printLink(w, f.getFileName() + "/");
                        }
                    }
                    else {
                        printLink(w, f.getFileName().toString());
                    }
                });
            } catch (IOException e) {
                throw e;
            }

            w.write("</body></html>");
            String s = w.toString();

            BayLog.trace("%s Directory: send contents: len=%d", tour, s.length());
            available = tour.res.sendResContent(tour.tourId, s.getBytes(), 0, s.length());

            try {
                while(!available) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                BayLog.error(e);
                throw new Sink(e.getMessage());
            }

            tour.res.endResContent(tour.tourId);

        } catch (IOException e) {
            BayLog.error(e);
        }
    }

    @Override
    protected void onTimer() {

    }


    private void printLink(StringWriter w, String path) {
        w.write("<a href='" + path + "'>");
        w.write(path);
        w.write("</a><br>");
    }

}
