package baykit.bayserver.docker.servlet.duck;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.Mimes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileServletDuck {

    class AsyncSendFile implements Runnable {

        final File file;
        final Object req;
        final Object res;
        final Tour tour;
        final int tourId;
        final ASyncContextDuck actx;

        public AsyncSendFile(
                File file,
                Object req,
                Object res,
                Tour tour,
                ASyncContextDuck actx) {
            this.file = file;
            this.req = req;
            this.res = res;
            this.tour = tour;
            this.tourId = tour.tourId;;
            this.actx = actx;
        }

        @Override
        public void run() {
            sendFile(req, res, tour, file);
            actx.complete();
        }
    }

    final ServletContextDuck ctx;
    final HttpServletRequestHelper reqHelper;
    final HttpServletResponseHelper resHelper;

    public FileServletDuck(ServletContextDuck ctx) {
        this.ctx = ctx;
        this.reqHelper = ctx.docker.reqHelper;
        this.resHelper = ctx.docker.resHelper;
    }

    public void doGet(Object req, Object res) throws IOException {

        File file = new File(reqHelper.getPathTranslated(req));
        if(!file.exists()) {
            resHelper.sendError(res, HttpStatus.NOT_FOUND, reqHelper.getRequestURI(req));
        }
        else if(file.isDirectory()) {
            resHelper.sendError(res, HttpStatus.FORBIDDEN, reqHelper.getRequestURI(req));
        }
        else {
            Tour tur = (Tour)reqHelper.getAttribute(req, ServletDocker.ATTR_TOUR);
            if(reqHelper.isAsyncSupported(req)) {
                ASyncContextDuck actx = reqHelper.startAsync(req, res);
                AsyncSendFile sf =
                        new AsyncSendFile(
                                file,
                                req,
                                res,
                                tur,
                                actx);
                actx.start(sf);
            }
            else {
                sendFile(req, res, tur, file);
            }
        }
    }

    private void sendFile(Object req, Object res, Tour tour, File file) {

        String mimeType = ctx.getMimeType(file.getName());
        if (mimeType == null)
            mimeType = "application/octet-stream";

        if (mimeType.startsWith("text/") && reqHelper.getCharacterEncoding(req) != null)
            mimeType = mimeType + "; charset=" + reqHelper.getCharacterEncoding(req);

        resHelper.setContentType(res, mimeType);
        resHelper.setContentLength(res, (int)file.length());

        try(FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[tour.ship.protocolHandler.maxReqPacketDataSize()];
            OutputStream out = resHelper.getOutputStreamObject(res);
            while (true) {
                int c;
                try {
                    c = in.read(buf);
                    if (c == -1)
                        break;
                } catch (IOException e) {
                    BayLog.error(e);
                    resHelper.sendError(res, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                    break;
                }
                out.write(buf, 0, c);
                while (!tour.res.available) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        break;
                    }
                }
            }
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }
}
