package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.RudderStateStore;
import yokohama.baykit.bayserver.docker.Trouble;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    public int bytesPosted;
    public int bytesLimit;
    public ContentConsumeListener resConsumeListener;
    boolean canCompress;
    GzipCompressor compressor;
    public boolean directBoarding;

    public TourRes(Tour tour) {
        this.tour = tour;
    }

    @Override
    public String toString() {
        return tour.toString();
    }

    void init() {
        directBoarding = BayServer.harbor.directBoarding();
    }

    @Override
    public void reset() {
        headers.clear();
        bytesPosted = 0;
        bytesLimit = 0;

        charset = null;
        headerSent = false;
        resConsumeListener = null;
        canCompress = false;
        compressor = null;
        directBoarding = false;
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


    /**
     * This method sends the response headers to the client.
     * Whether this process is carried out synchronously or asynchronously is uncertain"
     */
    public void sendHeaders(int checkId) throws IOException {
        //tour.checkTourId(checkId);

        if (tour.isZombie() || tour.isAborted())
            return;

        if (headerSent())
            return;

        if (tour.cargo != null) {
            tour.cargo.saveHeaders(headers);
        }

        this.bytesLimit = headers.contentLength();
        BayLog.debug("%s content length: %s", this, this.bytesLimit);

        // Compress check
        if (BayServer.harbor.gzipComp() &&
                headers.contains(Headers.CONTENT_TYPE) &&
                headers.contentType().toLowerCase().startsWith("text/") &&
                !headers.contains(Headers.CONTENT_ENCODING)) {
            String enc = tour.req.headers.getFast(Headers.ACCEPT_ENCODING);
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
                                errTour.res.sendResContent(Tour.TOUR_ID_NOCHECK, data, 0, data.length);
                                errTour.res.endResContent(Tour.TOUR_ID_NOCHECK);
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
        this.bytesPosted = 0;
    }

    /**
     * This method sends a part of the response content to the client.
     * Whether this process is synchronous or asynchronous is uncertain
     */
    public boolean sendResContent(int checkId, byte[] buf, int ofs, int len) throws IOException {
        if (buf == null)
            throw new NullPointerException();
        //tour.checkTourId(checkId);
        BayLog.debug("%s send content: len=%d cargo=%s", this, len, tour.cargo);
        if (tour.cargo != null) {
            tour.cargo.saveContent(buf, ofs, len);
        }

        // New listener
        DataConsumeListener lis = avail -> {
            consumed(checkId, len, avail);
        };

        if (tour.isZombie()) {
            BayLog.debug("%s zombie tour. return", this);
            lis.dataConsumed(true);
            return true;
        }

        if (!headerSent)
            throw new Sink("Header not sent");

        bytesPosted += len;
        BayLog.debug("%s posted res content len=%d posted=%d limit=%d",
                tour, len, bytesPosted, bytesLimit);

        boolean available = true;
        if(tour.isAborted()) {
            // Don't send peer any data. Do nothing
            BayLog.debug("%s Aborted tour. do nothing: %s state=%s", this, tour, tour.state);
            tour.changeState(checkId, Tour.TourState.ENDED);
            lis.dataConsumed(true);
        }
        else {
            if (canCompress) {
                getCompressor().compress(buf, ofs, len, lis);
            }
            else {
                try {
                    available = tour.ship.sendResContent(tour.shipId, tour, buf, ofs, len, lis);
                }
                catch(IOException e) {
                    BayLog.debug("%s error on sending resContent: %s", this, e);
                    lis.dataConsumed(true);
                    tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ABORTED);
                    throw e;
                }
            }
        }

        if (bytesLimit > 0 && bytesPosted > bytesLimit) {
            throw new IOException("Post data exceed content-length: " + bytesPosted + "/" + bytesLimit);
        }

        return available;
    }

    public void sendFile(String path, String charset) throws IOException, HttpException {
        DirectBoardingStore.FileInfo info = null;
        Rudder rd = null;
        int fileSize = -1;

        if (tour.ship.portDocker().protocol().equals("h1") &&
                !tour.ship.portDocker().secure() &&
                directBoarding) {
            /**
             * Send via directBoarding if the protocol is HTTP/1.x and unencrypted.
             */
            info = DirectBoardingStore.getFileInfo(path);
            rd = info.rudder;
            fileSize = info.fileLength;
            directBoarding = info.rudder != null;
        }
        else {
            directBoarding = false;
        }

        if (rd == null) {
            if (Files.isDirectory(Path.of(path))) {
                throw new DirectoryException();
            }
            else {
                switch (BayServer.harbor.fileMultiplexer()) {
                    case Spin:
                    case Pigeon: {
                        AsynchronousFileChannel ch =
                                AsynchronousFileChannel.open(Path.of(path), StandardOpenOption.READ);
                        rd = new AsynchronousFileChannelRudder(ch);
                        break;
                    }

                    case Job:
                    case Taxi: {
                        InputStream in = new FileInputStream(path);
                        ReadableByteChannel ch = Channels.newChannel(in);
                        rd = new ReadableByteChannelRudder(ch);
                        break;
                    }

                    default:
                        throw new Sink();
                }
                fileSize = (int)Files.size(Path.of(path));
            }
        }


        String mtype = null;
        int pos = path.lastIndexOf('.');
        if (pos >= 0) {
            String ext = path.substring(pos + 1).toLowerCase();
            mtype = Mimes.getType(ext);
        }

        if (mtype == null)
            mtype = "application/octet-stream";

        if (mtype.startsWith("text/") && charset != null)
            mtype = mtype + "; charset=" + charset;


        tour.res.headers.setContentType(mtype);
        tour.res.headers.setContentLength(fileSize);
        tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);

        if (directBoarding) {
            int turId = tour.id();
            tour.res.setConsumeListener(new ContentConsumeListener() {
                @Override
                public void contentConsumed(int len, boolean resume) {
                    try {
                        tour.res.endResContent(turId);
                    }
                    catch(IOException e) {
                        BayLog.debug(e);
                    }
                }
            });
            transferContent(Tour.TOUR_ID_NOCHECK, rd, 0, info.fileLength);
        }
        else {
            int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
            GrandAgent agt = GrandAgent.get(tour.ship.agentId);
            Multiplexer mpx;

            switch (BayServer.harbor.fileMultiplexer()) {
                case Spin: {
                    mpx = agt.spinMultiplexer;
                    break;
                }

                case Job: {
                    mpx = agt.jobMultiplexer;
                    break;
                }

                case Taxi: {
                    mpx = agt.taxiMultiplexer;
                    break;
                }

                case Pigeon: {
                    mpx = agt.pigeonMultiplexer;
                    break;
                }

                default:
                    throw new Sink();
            }

            final SendFileShip sendFileShip = new SendFileShip();
            PlainTransporter tp = new PlainTransporter(
                    mpx,
                    sendFileShip,
                    true,
                    bufsize,
                    false);

            sendFileShip.init(rd, tp, tour);
            int sid = sendFileShip.id();
            tour.res.setConsumeListener((len, resume) -> {
                if (resume) {
                    sendFileShip.resumeRead(sid);
                }
            });

            RudderState st = RudderStateStore.getStore(agt.agentId).rent();
            st.init(rd, tp);
            mpx.addRudderState(rd, st);
            mpx.reqRead(rd);
        }
    }

    public void transferContent(int checkId, Rudder fileRd, int ofs, int len) throws IOException {

        BayLog.debug("%s transfer content: ofs=%d len=%d", this, ofs, len);


        // New listener
        DataConsumeListener lis = avail -> {
            tour.checkTourId(checkId);
            resConsumeListener.contentConsumed(len, avail);
        };

        if (tour.isZombie()) {
            BayLog.debug("%s zombie tour. return", this);
            lis.dataConsumed(true);
            return;
        }

        if (!headerSent)
            throw new Sink("Header not sent");

        bytesPosted += len;
        BayLog.debug("%s posted res content len=%d posted=%d limit=%d",
                tour, len, bytesPosted, bytesLimit);

        if(tour.isAborted()) {
            // Don't send peer any data. Do nothing
            BayLog.debug("%s Aborted tour. do nothing: %s state=%s", this, tour, tour.state);
            tour.changeState(checkId, Tour.TourState.ENDED);
            lis.dataConsumed(true);
        }
        else {
            try {
                tour.ship.transferResContent(tour.shipId, tour, fileRd, ofs, len, lis);
            }
            catch(IOException e) {
                BayLog.debug("%s error on sending resContent: %s", this, e);
                lis.dataConsumed(true);
                tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ABORTED);
                throw e;
            }
        }
    }

    public boolean headerSent() {
        return headerSent;
    }


    /**
     * This method notifies the client that the response has ended.
     * Whether this process is synchronous or asynchronous is uncertain.
     * If it occurs synchronously, the tour instance will be disposed, and no further processing on the tour will be allowed
     */
    public void endResContent(int checkId) throws IOException {
        //tour.checkTourId(checkId);

        BayLog.debug("%s end ResContent", this);
        if(tour.isEnded()) {
            BayLog.debug("%s Tour is already ended (Ignore).", this);
            return;
        }

        if (!tour.isZombie() && tour.city != null)
            tour.city.log(tour);

        if (tour.cargo != null) {
            tour.cargo.endSave();
        }

        // send end message
        if (canCompress) {
            getCompressor().finish();
        }

        final boolean tourReturned[] = new boolean[] {false};
        DataConsumeListener lis = avail -> {
            tour.checkTourId(checkId);
            tour.ship.returnTour(tour);
            tourReturned[0] = true;
        };

        try {
            if(tour.isZombie() || tour.isAborted()) {
                // Don't send peer any data. Do nothing
                BayLog.debug("%s Aborted or zombie tour. do nothing: %s state=%s", this, tour, tour.state);
                lis.dataConsumed(true);
            }
            else {
                try {
                    tour.ship.sendEndTour(tour.shipId, tour, lis);
                }
                catch(IOException e) {
                    BayLog.debug("%s Error on sending end tour", this);
                    lis.dataConsumed(true);
                    throw e;
                }
            }
        }
        finally {
            // If tour is returned, we cannot change its state because
            // it will be disposed.
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

    /**
     * This method sends an HTTP error response to the client.
     * Whether this process is carried out synchronously or asynchronously is uncertain
     */
    public void sendError(int checkId, int status, String message, Throwable e) throws IOException {
        //tour.checkTourId(checkId);

        BayLog.debug("%s send error: status=%d, message=%s ex=%s", this, status, message, e == null ? "" : e.getMessage(), e);
        if (e != null)
            BayLog.debug(e);

        if (tour.isZombie())
            return;

        if(headerSent) {
            BayLog.debug("Try to send error after response header is sent (Ignore)");
            BayLog.debug("%s: status=%d, message=%s", this, status, message);
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
                body.append("<h1>").append(status).append(" ").append(str).append("</h1>").append(CharUtil.CRLF);

                headers.setStatus(status);

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
        endResContent(checkId);
    }


    private void sendRedirect(int checkId, int status, String location) throws IOException {
        //tour.checkTourId(checkId);

        try {
            if(headerSent) {
                BayLog.error("Try to redirect after response header is sent (Ignore)");
            }
            else {
                setConsumeListener(ContentConsumeListener.devNull);
                try {
                    headers.setStatus(status);
                    headers.set(Headers.LOCATION, location);

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
            endResContent(checkId);
        }

    }

    private void sendErrorContent(String content) throws IOException {


        // Set content type
        if (charset != null && !charset.equals("")) {
            headers.setContentType("text/html; charset=" + charset);
        } else {
            headers.setContentType("text/html");
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
            tour.ship.sendResContent(tour.shipId, tour, bytes, 0, bytes.length, null);
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

    /**
     * This method is called back when a part of the response data is actually sent to the client.
     * The bufferAvailable flag indicates whether the internal write buffer still has room
     * at the time of consumption; callers listening for resume use it to know when to
     * continue submitting content.
     */
    private void consumed(int checkId, int length, boolean bufferAvailable) {
        tour.checkTourId(checkId);
        if(resConsumeListener == null)
            throw new Sink("Consume listener is null");

        BayLog.debug("%s resConsumed: len=%d available=%b", tour, length, bufferAvailable);

        resConsumeListener.contentConsumed(length, bufferAvailable);
    }

}
