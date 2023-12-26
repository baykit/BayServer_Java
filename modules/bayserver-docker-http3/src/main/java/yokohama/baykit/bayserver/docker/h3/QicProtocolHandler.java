package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.*;
import io.quiche4j.Connection;
import io.quiche4j.Quiche;
import io.quiche4j.http3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class QicProtocolHandler
        extends ProtocolHandler<QicCommand, QicPacket, QicType>
        implements InboundHandler, Http3EventListener {

    enum ReqState {
        ReadHeader,
        ReadContent,
        End,
    };

    interface DelayedProcess {
        void process() throws Exception;
    }

    class QicTourExtra implements Reusable {

        ReqState reqState = ReqState.ReadHeader;
        ArrayList<DelayedProcess> delayedProcesses = new ArrayList<>();

        @Override
        public void reset() {
            delayedProcesses.clear();
            reqState = ReqState.ReadHeader;
        }
    }

    private static final int MAX_BUFFER_SIZE = 16384;

    static class PartialResponse {
        List<Http3Header> headers;
        byte[] body;
        boolean fin;
        long written;
        DataConsumeListener listener;
        boolean finished;

        PartialResponse(List<Http3Header> headers) {
            this(headers, null, 0, false, null);
        }

        PartialResponse(byte[] body, int ofs, DataConsumeListener lis) {
            this(null, body, ofs, false, lis);
        }

        PartialResponse(boolean fin, DataConsumeListener lis) {
            this(null, new byte[0], 0, fin, lis);
        }

        private PartialResponse(List<Http3Header> headers, byte[] body, int ofs, boolean fin, DataConsumeListener lis) {
            this.headers = headers;
            if(body != null)
                this.body = Arrays.copyOfRange(body, ofs, body.length);
            this.listener = lis;
            this.fin = fin;
            this.written = 0;
        }
    }

    final Connection con;
    final InetSocketAddress sender;
    final HashMap<Long, ArrayList<PartialResponse>> partialResponses = new HashMap<>();
    final Http3Config h3Config;
    final Postman postman;
    InboundShip ship;
    Http3Connection h3con;

    public static final String PROTOCOL = "HTTP/3";

    public QicProtocolHandler(Connection con, InetSocketAddress adr, Http3Config cfg, Postman postman) {
        this.con = con;
        this.sender = adr;
        this.h3Config = cfg;
        this.postman = postman;
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    public void setShip(InboundShip ship) {
        this.ship = ship;
    }

    ////////////////////////////////////////////
    // Implements ProtocolHandler
    ////////////////////////////////////////////

    @Override
    public String protocol() {
        return "h3";
    }

    @Override
    public int maxReqPacketDataSize() {
        return MAX_BUFFER_SIZE;
    }

    @Override
    public int maxResPacketDataSize() {
        return MAX_BUFFER_SIZE;
    }

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        byte[] bytes = buf.array();
        int n = con.recv(bytes, sender);

        if (n < bytes.length)
            BayLog.info("Packet Read failed ? %d/%d", n, bytes.length);

        if (n == Quiche.ErrorCode.DONE) {
            BayLog.debug("No data");
        }
        else if (n < 0) {
            throw new ProtocolException("Invalid packet: recvLen=" + QuicheErrorCode.getMessage(n));
        }
        else {
            // ESTABLISH H3 CONNECTION IF NONE
            Http3Connection h3Con = http3Connection();

            if (h3Con != null) {
                processH3Connection(h3Con);
            }
        }

        return NextSocketAction.Continue;
    }

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        BayLog.debug(e);
        return false;
    }

    ////////////////////////////////////////////
    // Implements InboundHandler
    ////////////////////////////////////////////

    @Override
    public void sendResHeaders(Tour tur) throws IOException {
        BayLog.debug("%s stm#%d sendResHeader cap=%d", tur, tur.req.key, con.streamCapacity(tur.req.key));

        final List<Http3Header> h3headers = new ArrayList<>();
        h3headers.add(new Http3Header(":status", Integer.toString(tur.res.headers.status())));

        tur.res.headers.headerNames().forEach(name -> {
            tur.res.headers.headerValues(name).forEach(value -> {
                h3headers.add(new Http3Header(name, value));
            });
        });

        if(BayServer.harbor.traceHeader()) {
            h3headers.forEach(hdr -> {
                BayLog.info("%s header %s: %s", tur, hdr.name(), hdr.value());
            });
        }

        long stmId = tur.req.key;

        long written = h3con.sendResponse(stmId, h3headers, false);

        if(written < 0) {
            // Error
            short h3err = Http3.ErrorCode.h3Error((short) written);
            short qerr = Http3.ErrorCode.quicheError((short) written);

            if (h3err == Http3.ErrorCode.STREAM_BLOCKED) {
                BayLog.warn("%s stm#%d sending header is blocked", tur, stmId);
                addPartialResponse(stmId, new PartialResponse(h3headers));
            }
            else if (h3err == Http3.ErrorCode.TRANSPORT_ERROR) {
                throw new IOException("h3: send body failed: " + QuicheErrorCode.getMessage(qerr) + "(" + qerr + ")");
            }
            else {
                throw new IOException("h3: send body failed: " + H3ErrorCode.getMessage(h3err) + "(" + h3err + ")");
            }
        }
        else {
            BayLog.debug("%s stm#%d send header succeed cap=%d", tur, stmId, con.streamCapacity(stmId));
            postPackets();
        }
    }

    @Override
    public void sendResContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendResContent len=%d posted=%d/%d", tur, stmId, len, tur.res.bytesPosted, tur.res.headers.contentLength());

        if(ofs > 0 || len  < bytes.length) {
            byte[] newBuf = new byte[len];
            System.arraycopy(bytes, ofs, newBuf, 0, len);
            bytes = newBuf;
        }

        PartialResponse part = null;
        if (partialResponses.containsKey(stmId)) {
            BayLog.trace("%s stm#%d waiting. put packet into queue", tur, stmId, len);
            part = new PartialResponse(bytes, 0, lis);
        }
        else {
            int cap = con.streamCapacity(stmId);
            BayLog.trace("%s stm#%d capacity=%d", tur, stmId, cap);

            if (cap < 0) {
                 // Error
                if (cap == Quiche.ErrorCode.STREAM_STOPPED) {
                    BayLog.error("%s stm#%d Stream stopped", tur, stmId);
                    part = new PartialResponse(bytes, 0, lis);
                }
                else {
                    throw quicheError("Get capacity failed: ", (int)stmId, cap);
                }
            }
            else if (cap == 0) {
                part = new PartialResponse(bytes, 0, lis);
            }
            else {
                long written = h3con.sendBody(tur.req.key, bytes, false);

                BayLog.debug("stm#%d send %d/%d bytes body", stmId, written, len);

                if (written < 0) {
                    // Error
                    if (written == Http3.ErrorCode.DONE) {
                        BayLog.debug("stm#%d send content DONE (^o^)", stmId);
                        part = new PartialResponse(bytes, 0, lis);
                    }
                    else if (written == Http3.ErrorCode.FRAME_UNEXPECTED) {
                        // Header not sent yet
                        part = new PartialResponse(bytes, 0, lis);
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
                        part = new PartialResponse(bytes, (int) written, lis);
                    }
                }
            }
        }

        if(part != null) {
            addPartialResponse(stmId, part);
        }
        else {
            if (lis != null)
                lis.dataConsumed();
        }

        postPackets();
    }

    @Override
    public void sendEndTour(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendEndTour", tur, stmId);
        boolean retry = false;

        if (partialResponses.containsKey(stmId)) {
            BayLog.debug("stm#%d put packet into que", stmId);
            retry = true;
        }
        else {

            int cap;
            long written = 0;

            cap = con.streamCapacity(stmId);
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
                    throw quicheError(tur + " stm#" + stmId + " Cannot get capacity: ", stmId, cap);
                }
            }
            else if (cap == 0) {
                BayLog.debug("%s stm#%d stream full, retry", tur, stmId);
                retry = true;
            }
            else {
                byte[] bytes = new byte[0];
                written = h3con.sendBody(stmId, bytes, true);
                BayLog.debug("stm#%d send finish data %d bytes", stmId, written);

                if (written < 0) {

                    if (written == -1) {
                        BayLog.warn("stm#%d send end content DONE (^o^)", stmId);
                    }
                    else if (written == Http3.ErrorCode.FRAME_UNEXPECTED) {
                        // Header not sent yet
                        BayLog.warn("stm#%d send end content error Frame Unexpected", stmId);
                        retry = true;
                    }
                    else {
                        short h3err = Http3.ErrorCode.h3Error((short) written);
                        short qerr = Http3.ErrorCode.quicheError((short) written);
                        if (h3err == Http3.ErrorCode.TRANSPORT_ERROR) {
                            throw quicheError("h3: send body failed: ", stmId, qerr);
                        }
                        else {
                            throw h3Error("h3: send body failed: ", stmId, h3err);
                        }
                    }
                }
            }
        }

        if(retry) {
            addPartialResponse(stmId, new PartialResponse(true, lis));
        }
        else if (lis != null) {
            lis.dataConsumed();
        }

        postPackets();
    }

    ////////////////////////////////////////////
    // Implements Http3EventListener
    ////////////////////////////////////////////

    public void onHeaders(long stmId, List<Http3Header> req_headers, boolean hasBody) {
        BayLog.debug("%s stm#%d onHeaders: %s", this, stmId, Thread.currentThread());

        try {
            Tour tur = getTour(stmId);
            if (tur == null) {
                tourIsUnavailable(stmId);
                return;
            }

            for (Http3Header hdr : req_headers) {
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

            BayLog.debug("%s stm#%d onHeader: method=%s uri=%s", tur, stmId, tur.req.method, tur.req.uri);

            int reqContLen = tur.req.headers.contentLength();
            if (reqContLen > 0) {
                tur.req.setReqContentLength(reqContLen);
            }

            try {
                startTour(tur);
                if (tur.req.headers.contentLength() <= 0) {
                    endReqContent(tur.id(), tur);
                }
            } catch (HttpException e) {
                BayLog.debug("%s Http error occurred: %s", this, e);

                if (reqContLen <= 0) {
                    // no post data
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                    return;
                } else {
                    // Delay send
                    tur.error = e;
                    tur.req.setContentHandler(ReqContentHandler.devNull);
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
        BayLog.debug("%s stm#%d onData: %s", this, stmId, Thread.currentThread());

        try {
            Tour tur = getTour(stmId);

            if(tur == null) {
                tourIsUnavailable(stmId);
                return;
            }

            byte[] buf = new byte[QicPacket.MAX_DATAGRAM_SIZE];
            for (int i = 0; i == 0; i++) {
                int nRead = h3con.recvBody(stmId, buf);

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
                    int sid = ship.shipId;
                    boolean success =
                            tur.req.postContent(
                                    Tour.TOUR_ID_NOCHECK,
                                    buf,
                                    0,
                                    nRead,
                                    (len, resume) -> {
                                        if (resume)
                                            tur.ship.resume(sid);
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
        BayLog.debug("%s stm#%d onFinished.", this, streamId);
        /*
        Tour tur = ship.getTour((int)streamId);
        try {
            endReqContent(Tour.TOUR_ID_NOCHECK, tur);
        } catch (Exception e) {
            BayLog.error(e);
        }
         */
    }

    ////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////

    IOException quicheError(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + ": " + QuicheErrorCode.getMessage(code) + "(" + code + ")");
    }

    IOException h3Error(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + H3ErrorCode.getMessage(code) + "(" + code + ")");
    }

    public Http3Connection http3Connection() {
        if (h3con == null) {
            if ((con.isInEarlyData() || con.isEstablished())) {
                BayLog.debug("%s Handshake done con=%d", this, con.getPointer());

                h3con = Http3Connection.withTransport(con, h3Config);

                BayLog.debug("%s New H3 connection: %s", this, h3con);
            }
        }
        return h3con;
    }

    Tour getTour(long stmId) {
        Tour tur = ship.getTour((int)stmId);
        return tur;
    }

    void addPartialResponse(long stmId, PartialResponse part) {
        ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if(parts == null) {
            parts = new ArrayList<>();
            partialResponses.put(stmId, parts);
        }
        parts.add(part);
        //BayLog.debug("stm#%d added: len=%d", stmId, parts.size());
    }

    void tourIsUnavailable(long stmId) throws IOException {
        BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
        Tour tur = ship.getTour((int) stmId, true);
        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
        //sip.agent.shutdown(false);
        return;
    }

    void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        // read shutdown
        BayLog.debug("%s endReqContent", tur);
        con.streamShutdown(tur.req.key, Quiche.Shutdown.READ, 0L);
        tur.req.endContent(checkId);
    }

    void startTour(Tour tur) throws HttpException {
        HttpUtil.parseHostPort(tur, 443);
        HttpUtil.parseAuthrization(tur);

        tur.req.protocol = PROTOCOL;
        tur.req.remotePort = sender.getPort();
        tur.req.remoteAddress = sender.getAddress().getHostAddress();
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req);

        tur.req.serverAddress = sender.getAddress().getHostAddress();
        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = true;

        tur.go();
    }


    /**
     * Process commands in HTTP3 onnection
     * @param h3con
     */
    void processH3Connection(Http3Connection h3con) throws IOException {

        BayLog.trace("%s processH3Connection", this);
        //flushWritable();

        // Polling h3 data
        while (true) {
            BayLog.trace("%s poll: %s", this, Thread.currentThread());
            long stmId = h3con.poll(this);
            //BayLog.info("%s stm#%d polled %s", this, streamId, Thread.currentThread());

            if (stmId == Quiche.ErrorCode.DONE) {
                BayLog.trace("%s No polling data: %s", this, Thread.currentThread());
                break;
            }

            if (stmId < 0) {
                BayLog.error("%s poll failed stm=%d", this, stmId);
                break;
            }
        }

        flushWritable();
    }

    void flushWritable() throws IOException {
        //BayLog.debug("%s flush writable", this);
        for(long stmId: con.writable()) {
            //BayLog.debug("%s stm#%d writable", this, stmId);
            onStreamWritable(stmId);
        }
    }

    void onStreamWritable(long stmId) throws IOException {
        ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if (parts == null)
            return;

        BayLog.debug("%s stm#%d writable qlen=%d", this, stmId, parts.size());
        ArrayList<DataConsumeListener> listeners = null;
        try {
            for (PartialResponse part : parts) {
                int cap = con.streamCapacity(stmId);

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
                        long n = h3con.sendResponse(stmId, part.headers, part.fin);

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
                        part.finished = true;

                    }
                    else {
                        // send body
                        byte[] body = Arrays.copyOfRange(part.body, (int) part.written, part.body.length);
                        long n = h3con.sendBody(stmId, body, part.fin);

                        int tryBytes = (int) (part.body.length - part.written);
                        BayLog.trace("%s stm#%d retry to send body %d bytes: try=%d written=%d/%d fin=%s", this, stmId, n, tryBytes, part.written, part.body.length, part.fin);

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
                                part.finished = true;
                            else
                                break;
                        }
                    }
                }
            }

            for (Iterator<PartialResponse> it = parts.iterator(); it.hasNext(); ) {
                PartialResponse part = it.next();
                if (!part.finished)
                    break;

                if (part.listener != null) {
                    if(listeners == null)
                        listeners = new ArrayList<>();
                    // notification is delayed to avoid deadlock
                    listeners.add(part.listener);
                }
                it.remove();
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

        postPackets();
    }

    boolean postPackets() throws IOException {
        boolean posted = false;
        while (true) {
            InetSocketAddress addr[] = new InetSocketAddress[1];
            QicPacket pkt = new QicPacket();

            int len = con.send(pkt.buf, addr);
            if(len == Quiche.ErrorCode.DONE) {
                //BayLog.debug("DONE");
                break;
            }

            if (len < 0) {
                throw new IOException("Quiche: cannot send packet:" + QuicheErrorCode.getMessage(len));
            }

            //BayLog.debug("%s post packet len=%d addr=%s", this, len, addr[0]);
            pkt.bufLen = len;
            postman.post(pkt.asBuffer(), addr[0], pkt, null);
            posted = true;
        }
        return posted;
    }

    boolean isClosed() {
        return con.isClosed();
    }

}
