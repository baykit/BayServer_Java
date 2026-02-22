package yokohama.baykit.bayserver.docker.h3;

import io.quiche4j.Connection;
import io.quiche4j.Quiche;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Connection;
import io.quiche4j.http3.Http3Header;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.protocol.CommandHandler;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourHandler;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

public class QicTicket implements TourHandler, CommandHandler {

    protected final static class PartialResponse {
        protected List<Http3Header> headers;
        protected byte[] body;
        protected long written;
        DataConsumeListener listener;
        boolean finished;
        boolean sent;

        PartialResponse(List<Http3Header> headers) {
            this(headers, null, 0, false, null);
        }

        PartialResponse(byte[] body, int ofs, boolean finished, DataConsumeListener lis) {
            this(null, body, ofs, finished, lis);
        }

        PartialResponse(List<Http3Header> headers, byte[] body, long written, boolean finished, DataConsumeListener lis) {
            this.headers = headers;
            this.body = body;
            this.written = written;
            this.finished = finished;
            this.listener = lis;
            this.sent = false;
        }
    }

    private static final String HEADER_NAME_STATUS = ":status";
    private static final String HEADER_NAME_SERVER = "server";
    private static final String HEADER_NAME_CONTENT_LENGTH = "content-length";
    private static final String PROTOCOL = "HTTP/3";

    Http3Connection h3Conn;
    Connection conn;
    HashMap<Long, ArrayList<PartialResponse>> partialResponses = new HashMap<>();
    InetSocketAddress sender;

    InboundShip h3Ship;
    int agentId;
    H3PortDocker portDocker;

    public QicTicket(Connection conn, InetSocketAddress sender, int agtId, H3PortDocker portDkr) {
        this.conn = conn;
        this.sender = sender;
        this.agentId = agtId;
        this.portDocker = portDkr;
    }

    ////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {

    }

    ////////////////////////////////////////////
    // Implements TourHandler
    ////////////////////////////////////////////
    @Override
    public void sendHeaders(Tour tur) throws IOException {
        //Http3Server.handleRequest(this, (long)tur.req.key, hdrs);
        BayLog.debug("%s stm#%d sendResHeaders", tur, tur.req.key);

        final List<Http3Header> h3headers = new ArrayList<>();
        h3headers.add(new Http3Header(HEADER_NAME_STATUS, Integer.toString(tur.res.headers.status())));
        //headers.add(new Http3Header(HEADER_NAME_SERVER, SERVER_NAME));
        //headers.add(new Http3Header(HEADER_NAME_CONTENT_LENGTH, Integer.toString(body.length)));

        tur.res.headers.headerNames().forEach(name -> {
            if(name.equalsIgnoreCase("connection"))
                return;

            tur.res.headers.headerValues(name).forEach(value -> {

                if(BayServer.harbor.traceHeader())
                    BayLog.info("%s resHeader %s: %s", tur, name, value);
                h3headers.add(new Http3Header(name, value));
            });
        });

        long stmId = tur.req.key;
        addPartialResponse(stmId, new PartialResponse(h3headers));
        /*

        long written = h3Conn.sendResponse(stmId, h3headers, false);

        if(written < 0) {
            // Error
            short h3err = Http3.ErrorCode.h3Error((short) written);
            short qerr = Http3.ErrorCode.quicheError((short) written);

            if (h3err == Http3.ErrorCode.STREAM_BLOCKED) {
                BayLog.warn("%s stm#%d sending header is blocked", tur, stmId);
                addPartialResponse(stmId, new PartialResponse(h3headers));
            }
            else if (h3err == Http3.ErrorCode.TRANSPORT_ERROR) {
                throw quicheError("h3: send header failed: ", stmId, qerr);
            }
            else {
                throw new IOException("h3: send header failed: " + H3ErrorCode.getMessage(h3err) + "(" + h3err + ")");
            }
        }
        else {
            BayLog.debug("%s stm#%d send header succeed cap=%d", tur, stmId, conn.streamCapacity(stmId));
        }

         */
    }

    @Override
    public void sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        BayLog.debug("%s stm#%d sendResContent len=%d", tur, tur.req.key, len);


        sendBody(tur, bytes, ofs, len, lis, false);
    }

    @Override
    public void sendEndTour(Tour tur, DataConsumeListener lis) throws IOException {
        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendEndTour", tur, stmId);


    }

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        return false;
    }

    ////////////////////////////////////////////
    // Http event handling
    ////////////////////////////////////////////

    public void onHeaders(long stmId, List<Http3Header> headers, boolean hasBody) {
        try {

            if (h3Ship == null) {
                h3Ship = new InboundShip();
                H3ProtocolHandler ph = new H3ProtocolHandler((CommandHandler) this);
                h3Ship.initInbound(null, agentId, null, portDocker, ph);

            }
            Tour tur = h3Ship.getTour((int) stmId);
            if (tur == null) {
                tourIsUnavailable(stmId);
                return;
            }

            for (Http3Header hdr : headers) {
                if (BayServer.harbor.traceHeader()) {
                    BayLog.info("%s stm#%d ReqHeader %s=%s", tur, stmId, hdr.name(), hdr.value());
                }
                switch (hdr.name().toLowerCase()) {
                    case ":path":
                        tur.req.uri = hdr.value();
                        break;
                    case ":authority":
                        tur.req.headers.add(Headers.HOST, hdr.value());
                        break;
                    case ":scheme":
                        tur.isSecure = hdr.value().equalsIgnoreCase("https");
                        break;
                    case ":method":
                        tur.req.method = hdr.value();
                        break;
                    default:
                        if (!hdr.name().startsWith(":"))
                            tur.req.headers.add(hdr.name(), hdr.value());
                        break;
                }
            }

            BayLog.trace("%s stm#%d onHeader: method=%s uri=%s", tur, stmId, tur.req.method, tur.req.uri);

            int reqContLen = tur.req.headers.contentLength();
            if (reqContLen > 0) {
                tur.req.setLimit(reqContLen);
            }

            try {
                startTour(tur);
                if (tur.req.headers.contentLength() <= 0) {
                    endReqContent(tur.id(), tur);
                }
            }
            catch (HttpException e) {
                BayLog.trace("%s Http error occurred: %s", this, e);

                if (reqContLen <= 0) {
                    // no post data
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                    return;
                }
                else {
                    // Delay send
                    tur.error = e;
                    tur.req.setReqContentHandler(ReqContentHandler.devNull);
                    return;
                }
            }
        }
        catch(Throwable e) {
            BayLog.error(e);
            return;
        }
    }

    public void onData(long stmId) {
        BayLog.info("%s stm#%d onData", this, stmId);

        try {
            Tour tur = getTour(stmId);

            if(tur == null) {
                tourIsUnavailable(stmId);
                return;
            }

            byte[] buf = new byte[QicPacket.MAX_DATAGRAM_SIZE];
            for (int i = 0; i == 0; i++) {
                int nRead = h3Conn.recvBody(stmId, buf);

                if (nRead < 0) {
                    // Error
                    if (nRead == Http3.ErrorCode.DONE) {
                        //endReqContent(Tour.TOUR_ID_NOCHECK, tur);
                        break;
                    }
                    else {
                        BayLog.error("%s stm#%d h3: recv body failed :%s(%d)", this, stmId, H3ErrorCode.getMessage(nRead), nRead);
                        break;
                    }
                }
                else if (nRead == 0) {
                    break;
                }
                else {
                    int sid = h3Ship.shipId;
                    boolean success =
                            tur.req.postReqContent(
                                    Tour.TOUR_ID_NOCHECK,
                                    buf,
                                    0,
                                    nRead,
                                    (len, resume) -> {
                                        if (resume)
                                            tur.ship.resumeRead(sid);
                                    });
                }
            }

            if (tur.req.bytesPosted >= tur.req.headers.contentLength()) {

                if(tur.error != null){
                    // Error has occurred on header completed
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, tur.error);
                }
                else {
                    try {
                        endReqContent(tur.id(), tur);
                    } catch (HttpException e) {
                        tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                    }
                }
            }
        }
        catch(Throwable e) {
            BayLog.error(e);
        }

    }

    public void onFinished(long streamId) {
        BayLog.info("%s stm#%d onFinished", this, streamId);
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    public void flushData() {
        for(Map.Entry<Long, ArrayList<PartialResponse>> entry: partialResponses.entrySet()) {

        }
    }

    public void handleWritable(long stmId) {
        final ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if (null == parts || parts.isEmpty())
            return;

        BayLog.debug("%s stm#%d writable qlen=%d", this, stmId, parts.size());
        ArrayList<DataConsumeListener> listeners = null;
        try {
            for (Iterator<PartialResponse> iter = parts.iterator(); iter.hasNext(); ) {
                PartialResponse part = iter.next();
                int cap = conn.streamCapacity(stmId);

                BayLog.trace("stm#%d writable capacity=%d", stmId, cap);
                if (cap < 0) {
                    // Error
                    if (cap == Quiche.ErrorCode.STREAM_STOPPED) {
                        BayLog.debug("stm#%d writable, but stream stopped", stmId);
                        break;
                    }
                    else {
                        throw h3Error("writable, but stream stopped", (int) stmId, cap);
                    }
                }
                else if (cap == 0) {
                    BayLog.debug("stm#%d writable, but no capacity", stmId);
                    break;
                } else {
                    BayLog.trace("%s handleWritable stm#%d part cap=%d", this, stmId, cap);

                    if (part.headers != null) {
                        // send header
                        long n = h3Conn.sendResponse(stmId, part.headers, part.finished);

                        if (n < 0) {
                            // Error
                            short h3err = Http3.ErrorCode.h3Error((short) n);
                            short qerr = Http3.ErrorCode.quicheError((short) n);

                            if (h3err == Http3.ErrorCode.TRANSPORT_ERROR || n == Http3.ErrorCode.STREAM_BLOCKED) {
                                if( qerr == Quiche.ErrorCode.DONE) {
                                    BayLog.debug("%s stm#%d retry to send header: DONE returned (retry)", this, stmId);
                                    break;
                                }
                                throw quicheError("retry to send header failed", (int)stmId, qerr);
                            }
                            else {
                                throw h3Error("h3: send body failed: ", (int)stmId, h3err);
                            }
                        }

                        BayLog.debug("%s stm#%d h3: retry to send header succeed", this, stmId);
                        part.sent = true;

                    }
                    else {
                        // send body
                        byte[] body = Arrays.copyOfRange(part.body, (int) part.written, part.body.length);
                        long n = h3Conn.sendBody(stmId, body, part.finished);

                        int tryBytes = (int) (part.body.length - part.written);
                        BayLog.trace("%s stm#%d retry to send body %d bytes: try=%d written=%d/%d fin=%s", this, stmId, n, tryBytes, part.written, part.body.length, part.finished);

                        if (n < 0) {
                            // Error
                            if (n == Http3.ErrorCode.DONE) {
                                BayLog.debug("%s stm#%d retry to send body: DONE returned (retry)", this, stmId);
                                break;
                            }
                            else {
                                BayLog.error("%s stm#%d h3: retry to send body failed :%s(%d)", this, stmId, H3ErrorCode.getMessage((int) n), n);
                                break;
                            }
                        }
                        else if (tryBytes > 0 && n == 0) {
                            BayLog.error("%s stm#%d h3: no data written", this, stmId);
                        }
                        else {
                            part.written += n;
                            if (part.written == part.body.length)
                                part.sent = true;
                            else
                                break;
                        }
                    }
                }
            }

            synchronized (parts) {
                for (Iterator<PartialResponse> it = parts.iterator(); it.hasNext(); ) {
                    PartialResponse part = it.next();
                    if (!part.sent)
                        break;

                    if (part.listener != null) {
                        if (listeners == null)
                            listeners = new ArrayList<>();
                        // notification is delayed to avoid deadlock
                        listeners.add(part.listener);
                    }
                    it.remove();
                }
            }
        }
        catch(IOException e){
            BayLog.error(e);
            parts.clear();
        }


        if(listeners != null)
            listeners.forEach(lis -> lis.dataConsumed());


        if(parts.isEmpty()) {
            partialResponses.remove(stmId);
        }
    }



    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private Tour getTour(long stmId) {
        Tour tur = h3Ship.getTour((int)stmId);
        return tur;
    }

    void tourIsUnavailable(long stmId) throws IOException {
        BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
        Tour tur = h3Ship.getTour((int) stmId, true);
        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
        //sip.agent.shutdown(false);
        return;
    }

    private void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        // read shutdown
        BayLog.trace("%s endReqContent", tur);
        conn.streamShutdown(tur.req.key, Quiche.Shutdown.READ, 0L);
        tur.req.endReqContent(checkId);
    }

    private void startTour(Tour tur) throws HttpException {
        tur.req.parseHostPort(443);
        tur.req.parseAuthorization();

        tur.req.protocol = PROTOCOL;
        tur.req.remotePort = sender.getPort();
        tur.req.remoteAddress = sender.getAddress().getHostAddress();
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req.remoteAddress);

        tur.req.serverAddress = sender.getAddress().getHostAddress();
        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = true;

        tur.go();
    }

    private void sendBody(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis, boolean finish) throws IOException {
        long stmId = tur.req.key;
        long written = -1;
        boolean retry = false;
        retry = true;
        /*
        if(ofs > 0 || len  < bytes.length) {
            byte[] newBuf = new byte[len];
            System.arraycopy(bytes, ofs, newBuf, 0, len);
            bytes = newBuf;
        }

        if (partialResponses.containsKey(stmId)) {
            BayLog.trace("%s stm#%d waiting. put packet into queue", tur, stmId, len);
            retry = true;
        }
        else {
            int cap = conn.streamCapacity(stmId);
            BayLog.trace("%s stm#%d capacity=%d", tur, stmId, cap);

            if (cap < 0) {
                // Error
                if (cap == Quiche.ErrorCode.STREAM_STOPPED) {
                    BayLog.error("%s stm#%d Stream stopped", tur, stmId);
                    retry = true;
                }
                else if (cap == Quiche.ErrorCode.INVALID_STREAM_STATE) {
                    BayLog.error("%s stm#%d Invalid stream (ignore)", tur, stmId);
                }
                else {
                    throw quicheError("Get capacity failed: ", (int)stmId, cap);
                }
            }
            else if (cap == 0) {
                retry = true;
            }
            else {
                written = h3Conn.sendBody(tur.req.key, bytes, false);

                BayLog.debug("stm#%d send %d/%d bytes body", stmId, written, len);

                if (written < 0) {
                    // Error
                    if (written == Http3.ErrorCode.DONE) {
                        BayLog.debug("stm#%d send content DONE (^o^)", stmId);
                        retry = true;
                    }
                    else if (written == Http3.ErrorCode.FRAME_UNEXPECTED) {
                        // Header not sent yet
                        retry = true;
                    }
                    else {
                        short h3err = Http3.ErrorCode.h3Error((short) written);
                        short qerr = Http3.ErrorCode.quicheError((short) written);
                        if (h3err == Http3.ErrorCode.TRANSPORT_ERROR) {
                            throw new IOException("h3: send body failed: " + QuicheErrorCode.getMessage(qerr) + "(" + qerr + ")");
                        }
                        else {
                            throw new IOException("h3: send body failed: " + H3ErrorCode.getMessage(h3err) + "(" + h3err + ")");
                        }
                    }
                }
                else {
                    if (written < len) {
                        BayLog.debug("stm#%d put remained packet into queue %d/%d", stmId, written, len);
                        retry = true;
                    }
                }
            }
        }

         */

        if(retry) {
            PartialResponse part;
            if(written > 0)
                part = new PartialResponse(bytes, (int) written, finish, lis);
            else
                part = new PartialResponse(bytes, 0, finish, lis);

            addPartialResponse(stmId, part);
        }
        else {
            if (lis != null)
                lis.dataConsumed();
        }
    }

    private void addPartialResponse(long stmId, PartialResponse part) {
        ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if(parts == null) {
            parts = new ArrayList<>();
            partialResponses.put(stmId, parts);
        }
        synchronized (parts) {
            parts.add(part);
        }
        //BayLog.debug("stm#%d added: len=%d", stmId, parts.size());
    }

    private IOException quicheError(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + ": " + QuicheErrorCode.getMessage(code) + "(" + code + ")");
    }

    IOException h3Error(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + H3ErrorCode.getMessage(code) + "(" + code + ")");
    }
}
