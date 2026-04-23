package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.docker.h3.command.CmdData;
import yokohama.baykit.bayserver.docker.h3.command.CmdFinished;
import yokohama.baykit.bayserver.docker.h3.command.CmdHeader;
import yokohama.baykit.bayserver.protocol.CommandHandler;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.croute.CrouteException;
import yokohama.baykit.croute.binding.QuicheBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QicInboundHandler implements CommandHandler<QicCommand>, InboundHandler, QicHandler {

    // HTTP/3 application error codes (RFC 9114 §8.1)
    private static final long H3_MESSAGE_ERROR = 0x10eL;

    QicProtocolHandler protocolHandler;

    public void init(QicProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    @Override
    public void reset() {

    }

    ////////////////////////////////////////////
    // Implements InboundHandler
    ////////////////////////////////////////////

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        long stmId = tur.req.key;
        long cap;
        try {
            cap = protocolHandler.con.streamCapacity(stmId);
        }
        catch(CrouteException e) {
            cap = -1;
        }
        BayLog.debug("%s stm#%d sendResHeader cap=%d", tur, stmId, cap);

        final List<yokohama.baykit.croute.h3.Headers.Header> h3headers = new ArrayList<>();
        h3headers.add(new yokohama.baykit.croute.h3.Headers.Header(":status", Integer.toString(tur.res.headers.status())));

        tur.res.headers.headerNames().forEach(name -> {
            tur.res.headers.headerValues(name).forEach(value -> {
                h3headers.add(new yokohama.baykit.croute.h3.Headers.Header(name, value));
            });
        });

        if(BayServer.harbor.traceHeader()) {
            h3headers.forEach(hdr -> {
                BayLog.info("%s header %s: %s", tur, hdr.name(), hdr.value());
            });
        }

        try {
            protocolHandler.h3con.sendHeaders(stmId,
                    yokohama.baykit.croute.h3.Headers.toFfi(h3headers), false);
            long capAfter;
            try {
                capAfter = protocolHandler.con.streamCapacity(stmId);
            }
            catch(CrouteException e) {
                capAfter = -1;
            }
            BayLog.debug("%s stm#%d send header succeed cap=%d", tur, stmId, capAfter);
            protocolHandler.postPackets();
        }
        catch(CrouteException e) {
            if (e.code == CrouteException.H3_ERR_STREAM_BLOCKED) {
                BayLog.warn("%s stm#%d sending header is blocked", tur, stmId);
                protocolHandler.addPartialResponse(stmId, new QicProtocolHandler.PartialResponse(h3headers));
            }
            else if (e.code == CrouteException.H3_ERR_TRANSPORT_ERROR) {
                long qerr = protocolHandler.peerOrLocalErrorCode();
                throw new IOException("h3: send header failed (transport): qerr=" + qerr);
            }
            else {
                throw new IOException("h3: send header failed: " + H3ErrorCode.getMessage(e.code) + "(" + e.code + ")");
            }
        }
    }

    @Override
    public boolean sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendResContent len=%d posted=%d/%d", tur, stmId, len, tur.res.bytesPosted, tur.res.headers.contentLength());

        if(ofs > 0 || len  < bytes.length) {
            byte[] newBuf = new byte[len];
            System.arraycopy(bytes, ofs, newBuf, 0, len);
            bytes = newBuf;
        }

        QicProtocolHandler.PartialResponse part = null;
        if (protocolHandler.partialResponses.containsKey(stmId)) {
            BayLog.trace("%s stm#%d waiting. put packet into queue len=%d", tur, stmId, len);
            part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
        }
        else {
            long cap;
            try {
                cap = protocolHandler.con.streamCapacity(stmId);
            }
            catch(CrouteException e) {
                if (e.code == CrouteException.ERR_STREAM_STOPPED) {
                    BayLog.error("%s stm#%d Stream stopped", tur, stmId);
                    part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                    cap = -1;
                }
                else {
                    throw protocolHandler.quicheError("Get capacity failed: ", stmId, e.code);
                }
            }
            BayLog.trace("%s stm#%d capacity=%d", tur, stmId, cap);

            if (part == null) {
                if (cap == 0) {
                    part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                }
                else {
                    try {
                        long written = protocolHandler.h3con.sendBody(stmId, bytes, false);
                        BayLog.debug("stm#%d send %d/%d bytes body", stmId, written, len);

                        if (written == 0) {
                            BayLog.debug("stm#%d send content DONE (^o^)", stmId);
                            part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                        }
                        else if (written < len) {
                            BayLog.debug("stm#%d put remained packet into queue %d/%d", stmId, written, len);
                            part = new QicProtocolHandler.PartialResponse(bytes, (int) written, lis);
                        }
                    }
                    catch(CrouteException e) {
                        if (e.code == CrouteException.H3_ERR_FRAME_UNEXPECTED) {
                            // Header not sent yet
                            part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                        }
                        else if (e.code == CrouteException.H3_ERR_TRANSPORT_ERROR) {
                            long qerr = protocolHandler.peerOrLocalErrorCode();
                            throw new IOException("h3: send body failed (transport): qerr=" + qerr);
                        }
                        else {
                            throw new IOException("h3: send body failed: " + H3ErrorCode.getMessage(e.code) + "(" + e.code + ")");
                        }
                    }
                }
            }
        }

        if(part != null) {
            protocolHandler.addPartialResponse(stmId, part);
        }
        else {
            if (lis != null)
                lis.dataConsumed(true);
        }

        protocolHandler.postPackets();
        return true;
    }

    @Override
    public void transferContent(Tour tur, Rudder fileRd, int ofs, int len, DataConsumeListener lis) {
        throw new Sink();
    }

    @Override
    public void sendEndTour(Tour tur, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendEndTour", tur, stmId);
        boolean retry = false;

        if (protocolHandler.partialResponses.containsKey(stmId)) {
            BayLog.debug("stm#%d put packet into que", stmId);
            retry = true;
        }
        else {

            long cap;
            try {
                cap = protocolHandler.con.streamCapacity(stmId);
            }
            catch(CrouteException e) {
                if (e.code == CrouteException.ERR_STREAM_STOPPED) {
                    BayLog.error("%s stm#%d Stream stopped", tur, stmId);
                    retry = true;
                    cap = -1;
                }
                else if (e.code == CrouteException.ERR_INVALID_STREAM_STATE) {
                    BayLog.error("%s stm#%d Invalid stream (ignore)", tur, stmId);
                    cap = -1;
                }
                else {
                    throw protocolHandler.quicheError(tur + " stm#" + stmId + " Cannot get capacity: ", stmId, e.code);
                }
            }
            BayLog.trace("%s stm#%d capacity=%d", tur, stmId, cap);

            if (!retry && cap == 0) {
                BayLog.debug("%s stm#%d stream full, retry", tur, stmId);
                retry = true;
            }
            else if (!retry && cap > 0) {
                try {
                    long written = protocolHandler.h3con.sendBody(stmId, new byte[0], true);
                    BayLog.debug("stm#%d send finish data %d bytes", stmId, written);
                }
                catch(CrouteException e) {
                    if (e.code == CrouteException.H3_ERR_FRAME_UNEXPECTED) {
                        BayLog.warn("stm#%d send end content error Frame Unexpected", stmId);
                        retry = true;
                    }
                    else if (e.code == CrouteException.H3_ERR_TRANSPORT_ERROR) {
                        long qerr = protocolHandler.peerOrLocalErrorCode();
                        throw new IOException("h3: send body failed (transport): qerr=" + qerr);
                    }
                    else {
                        throw protocolHandler.h3Error("h3: send body failed: ", stmId, e.code);
                    }
                }
            }
        }

        if(retry) {
            protocolHandler.addPartialResponse(stmId, new QicProtocolHandler.PartialResponse(true, lis));
        }
        else if (lis != null) {
            lis.dataConsumed(true);
        }

        protocolHandler.postPackets();
    }

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        BayLog.debug(e);
        return false;
    }

    ////////////////////////////////////////////
    // Implements QicHandler
    ////////////////////////////////////////////
    @Override
    public void handleHeaders(CmdHeader cmd) {
        BayLog.debug("%s stm#%d onHeaders: %s", this, cmd.stmId, Thread.currentThread());

        try {
            Tour tur = getTour(cmd.stmId);
            if (tur == null) {
                tourIsUnavailable(cmd.stmId);
                return;
            }

            // RFC 9114 §4.3 pseudo-header validation. h3spec (and conformant
            // clients) expect the server to reject malformed header sections
            // with H3_MESSAGE_ERROR (0x10e). quiche passes the headers through
            // unchecked, so we enforce the rules here.
            if (!validatePseudoHeaders(cmd)) {
                // Close the whole connection so h3spec's wait for CLOSE
                // succeeds. Stream-level rejection would also satisfy the
                // RFC, but we don't have per-stream error surface yet.
                closeWithH3Error(H3_MESSAGE_ERROR, "malformed pseudo-headers");
                return;
            }

            for (yokohama.baykit.croute.h3.Headers.Header hdr : cmd.reqHeaders) {
                if (BayServer.harbor.traceHeader()) {
                    BayLog.info("%s stm#%d ReqHeader %s=%s", tur, cmd.stmId, hdr.name(), hdr.value());
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

            BayLog.debug("%s stm#%d onHeader: method=%s uri=%s", tur, cmd.stmId, tur.req.method, tur.req.uri);

            int reqContLen = tur.req.headers.contentLength();
            if (reqContLen > 0) {
                tur.req.setLimit(reqContLen);
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

    @Override
    public void handleData(CmdData cmd) {
        BayLog.debug("%s stm#%d onData: %s", this, cmd.stmId, Thread.currentThread());

        try {
            Tour tur = getTour(cmd.stmId);

            if(tur == null) {
                tourIsUnavailable(cmd.stmId);
                return;
            }

            byte[] buf = new byte[QicPacket.MAX_DATAGRAM_SIZE];
            for (int i = 0; i == 0; i++) {
                long nRead;
                try {
                    nRead = protocolHandler.h3con.recvBody(cmd.stmId, buf);
                }
                catch(CrouteException e) {
                    BayLog.error("%s stm#%d h3: recv body failed :%s(%d)", this, cmd.stmId, H3ErrorCode.getMessage(e.code), e.code);
                    break;
                }

                if (nRead == CrouteException.H3_ERR_DONE) {
                    break;
                }
                if (nRead == 0) {
                    break;
                }

                int sid = protocolHandler.ship.shipId;
                boolean success =
                        tur.req.postReqContent(
                                Tour.TOUR_ID_NOCHECK,
                                buf,
                                0,
                                (int) nRead,
                                (length, resume) -> {
                                    if (resume)
                                        tur.ship.resumeRead(sid);
                                });
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

    @Override
    public void handleFinished(CmdFinished cmd) {
        BayLog.debug("%s stm#%d onFinished.", this, cmd.stmId);
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private Tour getTour(long stmId) {
        Tour tur = protocolHandler.ship.getTour((int)stmId);
        return tur;
    }

    void tourIsUnavailable(long stmId) throws IOException {
        BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
        Tour tur = protocolHandler.ship.getTour((int) stmId, true);
        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
        return;
    }

    private void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        BayLog.debug("%s endReqContent", tur);
        protocolHandler.con.streamShutdown(tur.req.key, QuicheBinding.SHUTDOWN_READ, 0L);
        tur.req.endReqContent(checkId);
    }

    private void startTour(Tour tur) throws HttpException {
        tur.req.parseHostPort(443);
        tur.req.parseAuthorization();

        tur.req.protocol = protocolHandler.PROTOCOL;
        tur.req.remotePort = protocolHandler.sender.getPort();
        tur.req.remoteAddress = protocolHandler.sender.getAddress().getHostAddress();
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req.remoteAddress);

        tur.req.serverAddress = protocolHandler.sender.getAddress().getHostAddress();
        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = true;

        tur.go();
    }

    /**
     * Enforce the pseudo-header rules from RFC 9114 §4.3 on an inbound request:
     *
     * <ul>
     *   <li>A pseudo-header must not appear after any regular field.</li>
     *   <li>No pseudo-header name may appear twice.</li>
     *   <li>Response-only pseudo-headers (e.g. {@code :status}) and any
     *       unrecognised {@code :*} name are prohibited in requests.</li>
     *   <li>{@code :method} is mandatory; {@code :scheme} / {@code :path} are
     *       mandatory for non-CONNECT requests (CONNECT relaxes them).</li>
     * </ul>
     *
     * @return {@code true} if the header section is well-formed, {@code false}
     *         if the caller must close the connection with H3_MESSAGE_ERROR.
     */
    private boolean validatePseudoHeaders(CmdHeader cmd) {
        Set<String> seenPseudo = new HashSet<>();
        boolean sawRegular = false;
        String method = null;
        String scheme = null;
        String path = null;
        String authority = null;
        for (yokohama.baykit.croute.h3.Headers.Header hdr : cmd.reqHeaders) {
            String name = hdr.name();
            if (name.isEmpty()) {
                BayLog.debug("%s stm#%d empty header name", this, cmd.stmId);
                return false;
            }
            if (name.charAt(0) == ':') {
                if (sawRegular) {
                    BayLog.debug("%s stm#%d pseudo-header %s after regular fields", this, cmd.stmId, name);
                    return false;
                }
                if (!seenPseudo.add(name)) {
                    BayLog.debug("%s stm#%d duplicated pseudo-header %s", this, cmd.stmId, name);
                    return false;
                }
                switch (name) {
                    case ":method":    method    = hdr.value(); break;
                    case ":scheme":    scheme    = hdr.value(); break;
                    case ":path":      path      = hdr.value(); break;
                    case ":authority": authority = hdr.value(); break;
                    default:
                        // Any other :xxx (notably :status) is a response or
                        // unknown pseudo-header; prohibited in a request.
                        BayLog.debug("%s stm#%d prohibited pseudo-header %s", this, cmd.stmId, name);
                        return false;
                }
            } else {
                sawRegular = true;
            }
        }
        if (method == null) {
            BayLog.debug("%s stm#%d missing :method", this, cmd.stmId);
            return false;
        }
        boolean isConnect = "CONNECT".equalsIgnoreCase(method);
        if (!isConnect) {
            if (scheme == null) {
                BayLog.debug("%s stm#%d missing :scheme", this, cmd.stmId);
                return false;
            }
            if (path == null || path.isEmpty()) {
                BayLog.debug("%s stm#%d missing :path", this, cmd.stmId);
                return false;
            }
            // RFC 9110 §7.2 / RFC 9114 §4.3.1: an HTTP request must identify a
            // target host. HTTP/3 does this through :authority (or a Host
            // header, but that is explicitly discouraged for H3). Treat a
            // request with neither as malformed.
            if (authority == null) {
                boolean hasHost = false;
                for (yokohama.baykit.croute.h3.Headers.Header h : cmd.reqHeaders) {
                    if ("host".equalsIgnoreCase(h.name())) { hasHost = true; break; }
                }
                if (!hasHost) {
                    BayLog.debug("%s stm#%d missing :authority and Host", this, cmd.stmId);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Move the underlying QUIC connection into closing state with an
     * application-layer error code and flush the CONNECTION_CLOSE so the peer
     * sees the response before we tear down. quiche's {@code quiche_conn_close}
     * only enqueues the frame; the following {@code postPackets()} is what
     * actually puts it on the wire.
     */
    private void closeWithH3Error(long errorCode, String reason) {
        BayLog.debug("%s closing H3 connection: code=0x%x reason=%s", this, errorCode, reason);
        byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes();
        try {
            protocolHandler.con.closeConnection(true, errorCode, reasonBytes);
        }
        catch(CrouteException e) {
            BayLog.debug("%s closeConnection failed: %s", this, e.getMessage());
        }
        try {
            protocolHandler.postPackets();
        }
        catch(IOException e) {
            BayLog.debug("%s postPackets after close failed: %s", this, e.getMessage());
        }
    }
}
