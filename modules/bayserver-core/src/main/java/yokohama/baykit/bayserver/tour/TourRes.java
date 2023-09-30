package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.transporter.SpinReadTransporter;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.*;
import yokohama.baykit.bayserver.util.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
    SendFileYacht yacht;

    public TourRes(Tour tour) {
        this.tour = tour;
    }

    @Override
    public String toString() {
        return tour.toString();
    }

    void init() {
        this.yacht = new SendFileYacht();
    }

    @Override
    public void reset() {
        headers.clear();
        bytesPosted = 0;
        bytesConsumed = 0;
        bytesLimit = 0;

        charset = null;
        headerSent = false;
        yacht.reset();
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

        if (tour.isZombie())
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
        tour.ship.sendHeaders(tour.shipId, tour);

        headerSent = true;
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

        BayLog.debug("%s sendContent len=%d", tour, len);

        if (canCompress) {
            getCompressor().compress(buf, ofs, len, lis);
        } else {
            try {
                tour.ship.sendResContent(tour.shipId, tour, buf, ofs, len, lis);
            }
            catch(IOException e) {
                lis.dataConsumed();
                throw e;
            }
        }
        bytesPosted += len;

        BayLog.debug("%s posted res content len=%d posted=%d limit=%d consumed=%d",
                    tour, len, bytesPosted, bytesLimit, bytesConsumed);
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

    public void endContent(int checkId) throws IOException {
        tour.checkTourId(checkId);

        BayLog.debug("%s end ResContent", this);

        if (!tour.isZombie() && tour.city != null)
            tour.city.log(tour);

        // send end message
        if (canCompress) {
            getCompressor().finish();
        }

        DataConsumeListener lis = () -> tour.ship.returnTour(tour);

        try {
            tour.ship.sendEndTour(tour.shipId, checkId, tour, lis);
        }
        catch(IOException e) {
            lis.dataConsumed();
            throw e;
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

        if (tour.isZombie())
            return;

        if(headerSent) {
            BayLog.warn("Try to send error after response header is sent (Ignore)");
            BayLog.warn("%s: status=%d, message=%s", this, status, message);
            if (e != null)
                BayLog.error(e);
        }
        else {
            setConsumeListener(ContentConsumeListener.devNull);
            tour.ship.sendError(tour.shipId, tour, status, message, e);
            headerSent = true;
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

            if (async) {
                int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
                switch(BayServer.harbor.fileSendMethod()) {
                    case Spin: {
                        int timeout = 10;
                        SpinReadTransporter tp = new SpinReadTransporter(bufsize);
                        yacht.init(tour, file, tp);
                        tp.init(tour.ship.agent.spinHandler, yacht, new FileInputStream(file), (int)file.length(), timeout,null);
                        tp.openValve();
                        break;
                    }

                    case Taxi:{
                        ReadFileTaxi txi = new ReadFileTaxi(bufsize);
                        yacht.init(tour, file, txi);
                        txi.init(new FileInputStream(file), yacht);
                        if(!TaxiRunner.post(txi)) {
                            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                        }
                        break;
                    }

                    default:
                        throw new Sink();
                }

            } else {
                new SendFileTrain(tour, file).depart();
            }
        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, file.getPath());
        }
    }

    private void sendRedirect(int checkId, int status, String location) throws IOException {
        tour.checkTourId(checkId);

        if(headerSent) {
            BayLog.error("Try to redirect after response header is sent (Ignore)");
        }
        else {
            setConsumeListener(ContentConsumeListener.devNull);
            tour.ship.sendRedirect(tour.shipId, tour, status, location);
            headerSent = true;
        }

        endContent(checkId);
    }

    private GzipCompressor getCompressor() throws IOException {
        if (compressor == null) {
            compressor = new GzipCompressor((newBuf, newOfs, newLen, lis) -> {
                tour.ship.sendResContent(tour.shipId, tour, newBuf, newOfs, newLen, lis);
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
