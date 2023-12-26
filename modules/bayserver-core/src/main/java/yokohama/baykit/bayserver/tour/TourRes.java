package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.transporter.SpinReadTransporter;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.common.ReadStreamTaxi;
import yokohama.baykit.bayserver.common.ReadStreamTrain;
import yokohama.baykit.bayserver.agent.transporter.TcpDataListener;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.docker.Trouble;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

public class TourRes implements Reusable {
    private final Tour tour;
    /**
     * Response header info
     */
    public Headers headers = new Headers();

    String charset;
    public boolean headerSent;

    /**
     * Response content info
     */
    public boolean available;
    public int bytesPosted;
    public int bytesConsumed;
    public int bytesLimit;
    public ContentConsumeListener resConsumeListener;
    boolean canCompress;
    GzipCompressor compressor;
    SendFileShip sendFileShip;

    public TourRes(Tour tour) {
        this.tour = tour;
    }

    @Override
    public String toString() {
        return tour.toString();
    }

    void init() {
        this.sendFileShip = new SendFileShip();
    }

    @Override
    public void reset() {
        headers.clear();
        bytesPosted = 0;
        bytesConsumed = 0;
        bytesLimit = 0;

        charset = null;
        headerSent = false;
        sendFileShip.reset();
        available = false;
        resConsumeListener = null;
        canCompress = false;
        compressor = null;
    }

    public String charset() {
        if (StringUtil.empty(charset))
            return null;
        else
            return charset;
    }

    public void setCharset(String charset) {
        this.charset = StringUtil.parseCharset(charset);
    }


    public void sendHeaders(int checkId) throws IOException {
        tour.checkTourId(checkId);

        if (tour.isZombie() || tour.isAborted())
            return;

        if (headerSent())
            return;

        this.bytesLimit = headers.contentLength();

        // Compress check
        if (BayServer.harbor.gzipComp() &&
                headers.contains(Headers.CONTENT_TYPE) &&
                headers.contentType().toLowerCase().startsWith("text/") &&
                !headers.contains(Headers.CONTENT_ENCODING)) {
            String enc = tour.req.headers.get(Headers.ACCEPT_ENCODING);
            if (enc != null) {
                StringTokenizer st = new StringTokenizer(enc, ",");
                while (st.hasMoreTokens()) {
                    if (st.nextToken().trim().equalsIgnoreCase("gzip")) {
                        canCompress = true;
                        headers.set(Headers.CONTENT_ENCODING, "gzip");
                        headers.remove(Headers.CONTENT_LENGTH);
                        break;
                    }
                }
            }
        }

        try {
            boolean handled = false;
            if (!tour.errorHandling && tour.res.headers.status() >= 400) {
                Trouble trb = BayServer.harbor.trouble();
                if (trb != null) {
                    Trouble.Command cmd = trb.find(tour.res.headers.status());
                    if (cmd != null) {
                        Tour errTour = tour.ship.getErrorTour();
                        errTour.req.uri = cmd.target;
                        tour.req.headers.copyTo(errTour.req.headers);
                        tour.res.headers.copyTo(errTour.res.headers);
                        errTour.req.remotePort = tour.req.remotePort;
                        errTour.req.remoteAddress = tour.req.remoteAddress;
                        errTour.req.serverAddress = tour.req.serverAddress;
                        errTour.req.serverPort = tour.req.serverPort;
                        errTour.req.serverName = tour.req.serverName;
                        errTour.res.headerSent = tour.res.headerSent;
                        tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ZOMBIE);
                        switch (cmd.method) {
                            case GUIDE: {
                                try {
                                    errTour.go();
                                } catch (HttpException e) {
                                    throw new IOException(e);
                                }
                                break;
                            }

                            case TEXT: {
                                tour.ship.sendHeaders(tour.ship.shipId, errTour);
                                byte[] data = cmd.target.getBytes();
                                errTour.res.sendContent(Tour.TOUR_ID_NOCHECK, data, 0, data.length);
                                errTour.res.endContent(Tour.TOUR_ID_NOCHECK);
                                break;
                            }

                            case REROUTE: {
                                errTour.res.sendHttpException(Tour.TOUR_ID_NOCHECK, HttpException.movedTemp(cmd.target));
                                break;
                            }
                        }
                        handled = true;
                    }
                }
            }

            if (!handled) {
                tour.ship.sendHeaders(tour.shipId, tour);
            }
        }
        catch(IOException e) {
            tour.changeState(checkId, Tour.TourState.ABORTED);
            throw e;
        }
        finally {
            headerSent = true;
        }
    }

    public void setConsumeListener(ContentConsumeListener listener) {
        this.resConsumeListener = listener;
        this.bytesConsumed = 0;
        this.bytesPosted = 0;
        this.available = true;
    }

    public synchronized boolean sendContent(int checkId, byte[] buf, int ofs, int len) throws IOException {
        if (buf == null)
            throw new NullPointerException();
        tour.checkTourId(checkId);
        BayLog.debug("%s send content: len=%d", this, len);


        // New listener
        DataConsumeListener lis = () -> {
            consumed(checkId, len);
        };

        if (tour.isZombie()) {
            BayLog.debug("%s zombie return", this);
            lis.dataConsumed();
            return true;
        }

        if (!headerSent)
            throw new Sink("BUG!: Header not sent");

        if (resConsumeListener == null)
            throw new Sink("Response consume listener is null");

        bytesPosted += len;
        BayLog.debug("%s posted res content len=%d posted=%d limit=%d consumed=%d",
                tour, len, bytesPosted, bytesLimit, bytesConsumed);

        if(tour.isZombie() || tour.isAborted()) {
            // Don't send peer any data. Do nothing
            BayLog.debug("%s Aborted or zombie tour. do nothing: %s state=%s", this, tour, tour.state);
            tour.changeState(checkId, Tour.TourState.ENDED);
            lis.dataConsumed();
        }
        else {
            if (canCompress) {
                getCompressor().compress(buf, ofs, len, lis);
            }
            else {
                try {
                    tour.ship.sendResContent(tour.shipId, tour, buf, ofs, len, lis);
                }
                catch(IOException e) {
                    lis.dataConsumed();
                    tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ABORTED);
                    throw e;
                }
            }
        }

        if (bytesLimit > 0 && bytesPosted > bytesLimit) {
            throw new ProtocolException("Post data exceed content-length: " + bytesPosted + "/" + bytesLimit);
        }

        boolean oldAvailable = available;
        if(!bufferAvailable())
            available = false;
        if(oldAvailable && !available)
            BayLog.debug("%s response unavailable (_ _): posted=%d consumed=%d", this,  bytesPosted, bytesConsumed);

        return available;
    }

    public boolean headerSent() {
        return headerSent;
    }

    public synchronized void endContent(int checkId) throws IOException {
        tour.checkTourId(checkId);

        BayLog.debug("%s end ResContent", this);
        if(tour.isEnded()) {
            BayLog.debug("%s Tour is already ended (Ignore).", this);
            return;
        }

        if (!tour.isZombie() && tour.city != null)
            tour.city.log(tour);

        // send end message
        if (canCompress) {
            getCompressor().finish();
        }

        final boolean tourReturned[] = new boolean[] {false};
        DataConsumeListener lis = () -> {
            tour.checkTourId(checkId);
            tour.ship.returnTour(tour);
            tourReturned[0] = true;
        };

        try {
            if(tour.isZombie() || tour.isAborted()) {
                // Don't send peer any data. Do nothing
                BayLog.debug("%s Aborted or zombie tour. do nothing: %s state=%s", this, tour, tour.state);
                lis.dataConsumed();
            }
            else {
                try {
                    tour.ship.sendEndTour(tour.shipId, tour, lis);
                }
                catch(IOException e) {
                    BayLog.debug("%s Error on sending end tour", this);
                    lis.dataConsumed();
                    throw e;
                }
            }
        }
        finally {
            // If tour is returned, we cannot change its state because
            // it will become uninitialized.
            BayLog.debug("%s is returned: %s", this, tourReturned[0]);
            if(!tourReturned[0])
                tour.changeState(checkId, Tour.TourState.ENDED);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Methods to sending error
    ////////////////////////////////////////////////////////////////////////////////
    public void sendHttpException(int checkId, HttpException e) throws IOException {
        if (e.status == HttpStatus.MOVED_TEMPORARILY || e.status == HttpStatus.MOVED_PERMANENTLY)
            sendRedirect(checkId, e.status, e.location);
        else
            sendError(checkId, e.status, e.getMessage(), e);
    }

    public void sendError(int checkId, int status, String message) throws IOException {
        sendError(checkId, status, message, null);
    }

    public void sendError(int checkId, int status, String message, Throwable e) throws IOException {
        tour.checkTourId(checkId);

        BayLog.debug("%s send error: status=%d, message=%s ex=%s", this, status, message, e == null ? "" : e.getMessage(), e);
        if (e != null)
            BayLog.debug(e);

        if (tour.isZombie())
            return;

        if(headerSent) {
            BayLog.debug("Try to send error after response header is sent (Ignore)");
            BayLog.debug("%s: status=%d, message=%s", this, status, message);
            if (e != null)
                BayLog.error(e);
        }
        else {
            setConsumeListener(ContentConsumeListener.devNull);

            if(tour.isZombie() || tour.isAborted()) {
                // Don't send peer any data. Do nothing
                BayLog.debug("%s Aborted or zombie tour. do nothing: %s state=%s", this, tour, tour.state);
            }
            else {
                StringBuilder body = new StringBuilder();

                // Create body
                String str = HttpStatus.description(status);

                // print status
                body.append("<h1>").append(status).append(" ").append(str).append("</h1>").append(Constants.CRLF);

                tour.res.headers.setStatus(status);

                try {
                    sendErrorContent(body.toString());
                }
                catch(IOException ex) {
                    BayLog.debug(e, "%s Error in sending error", this);
                    tour.changeState(checkId, Tour.TourState.ABORTED);
                }
                headerSent = true;
            }
        }
        endContent(checkId);
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Sending file methods
    ////////////////////////////////////////////////////////////////////////////////
    public void sendFile(int checkId, File file, String charset, boolean async) throws HttpException {
        tour.checkTourId(checkId);

        if (tour.isZombie())
            return;

        if (file.isDirectory()) {
            throw new HttpException(HttpStatus.FORBIDDEN, file.getPath());
        } else if (!file.exists()) {
            throw new HttpException(HttpStatus.NOT_FOUND, file.getPath());
        }

        String mimeType = null;

        String rname = file.getName();
        int pos = rname.lastIndexOf('.');
        if (pos >= 0) {
            String ext = rname.substring(pos + 1).toLowerCase();
            mimeType = Mimes.getType(ext);
        }

        if (mimeType == null)
            mimeType = "application/octet-stream";

        if (mimeType.startsWith("text/") && charset() != null)
            mimeType = mimeType + "; charset=" + charset;

        //resHeaders.setStatus(HttpStatus.OK);
        headers.setContentType(mimeType);
        headers.setContentLength(file.length());
        try {
            sendHeaders(Tour.TOUR_ID_NOCHECK);

            InputStream in = new FileInputStream(file);
            if (async) {
                int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
                switch(BayServer.harbor.fileSendMethod()) {
                    case Spin: {
                        int timeout = 10;
                        SpinReadTransporter tp = new SpinReadTransporter(bufsize);
                        sendFileShip.init(in, tour, tp);
                        tp.init(
                                tour.ship.agent.spinHandler,
                                new TcpDataListener(sendFileShip),
                                new FileInputStream(file),
                                (int)file.length(),
                                timeout,
                                null);
                        int sid = sendFileShip.id();
                        tour.res.setConsumeListener((len, resume) -> {
                            if(resume) {
                                sendFileShip.resume(sid);
                            }
                        });
                        tp.openValve();
                        break;
                    }

                    case Taxi:{
                        ReadStreamTaxi txi = new ReadStreamTaxi(tour.ship.agent.agentId, bufsize);
                        sendFileShip.init(in, tour, txi);
                        txi.init(in, new TcpDataListener(sendFileShip));
                        int sid = sendFileShip.id();
                        tour.res.setConsumeListener((len, resume) -> {
                            if(resume) {
                                sendFileShip.resume(sid);
                            }
                        });
                        if(!TaxiRunner.post(tour.ship.agent.agentId, txi)) {
                            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                        }
                        break;
                    }

                    default:
                        throw new Sink();
                }

            } else {
                new ReadStreamTrain(sendFileShip, tour).depart();
            }
        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, file.getPath());
        }
    }

    private void sendRedirect(int checkId, int status, String location) throws IOException {
        tour.checkTourId(checkId);

        try {
            if(headerSent) {
                BayLog.error("Try to redirect after response header is sent (Ignore)");
            }
            else {
                setConsumeListener(ContentConsumeListener.devNull);
                try {
                    Headers hdr = tour.res.headers;
                    hdr.setStatus(status);
                    hdr.set(Headers.LOCATION, location);

                    String body = "<H2>Document Moved.</H2><BR>" + "<A HREF=\""
                            + location + "\">" + location + "</A>";

                    sendErrorContent(body);
                }
                catch(IOException e) {
                    tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ABORTED);
                    throw e;
                }
                finally {
                    headerSent = true;
                }
            }
        }
        finally {
            endContent(checkId);
        }

    }

    private void sendErrorContent(String content) throws IOException {

        // Get charset
        String charset = tour.res.charset();

        // Set content type
        if (charset != null && !charset.equals("")) {
            tour.res.headers.setContentType("text/html; charset=" + charset);
        } else {
            tour.res.headers.setContentType("text/html");
        }

        byte[] bytes = null;
        if (content != null && !content.equals("")) {
            // Create writer
            if (charset != null && !charset.equals("")) {
                bytes = content.getBytes(charset);
            } else {
                bytes = content.getBytes();
            }
            tour.res.headers.setContentLength(bytes.length);
        }
        tour.ship.sendHeaders(tour.ship.shipId, tour);

        if (bytes != null)
            tour.ship.sendResContent(tour.ship.shipId, tour, bytes, 0, bytes.length, null);
    }

    private GzipCompressor getCompressor() throws IOException {
        if (compressor == null) {
            int sipId = tour.ship.shipId;
            int turId = tour.tourId;
            compressor = new GzipCompressor((newBuf, newOfs, newLen, lis) -> {
                try {
                    tour.ship.sendResContent(sipId, tour, newBuf, newOfs, newLen, lis);
                }
                catch(IOException e) {
                    tour.changeState(turId, Tour.TourState.ABORTED);
                    throw e;
                }
            });
        }
        return compressor;
    }

    private synchronized void consumed(int checkId, int length) {
        tour.checkTourId(checkId);
        if (resConsumeListener == null)
            throw new Sink("Response consume listener is null");

        bytesConsumed += length;

        BayLog.debug("%s resConsumed: len=%d posted=%d consumed=%d limit=%d",
                tour, length, bytesPosted, bytesConsumed, bytesLimit);

        boolean resume = false;
        boolean oldAvailable = available;
        if(bufferAvailable())
            available = true;
        if(!oldAvailable && available) {
            BayLog.debug("%s response available (^o^): posted=%d consumed=%d", this,  bytesPosted, bytesConsumed);
            resume = true;
        }

        if(!tour.isRunning()) {
            resConsumeListener.contentConsumed(length, resume);
        }
    }

    private boolean bufferAvailable() {
        return bytesPosted - bytesConsumed < BayServer.harbor.tourBufferSize();
    }

}
