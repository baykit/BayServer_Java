package yokohama.baykit.bayserver.docker.h3;

import io.quiche4j.Quiche;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Header;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.docker.h3.command.CmdData;
import yokohama.baykit.bayserver.docker.h3.command.CmdFinished;
import yokohama.baykit.bayserver.docker.h3.command.CmdHeader;
import yokohama.baykit.bayserver.protocol.CommandHandler;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QicInboundHandler implements CommandHandler<QicCommand>, InboundHandler, QicHandler {

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
        BayLog.debug("%s stm#%d sendResHeader cap=%d", tur, tur.req.key, protocolHandler.con.streamCapacity(tur.req.key));

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

        long written = protocolHandler.h3con.sendResponse(stmId, h3headers, false);

        if(written < 0) {
            // Error
            short h3err = Http3.ErrorCode.h3Error((short) written);
            short qerr = Http3.ErrorCode.quicheError((short) written);

            if (h3err == Http3.ErrorCode.STREAM_BLOCKED) {
                BayLog.warn("%s stm#%d sending header is blocked", tur, stmId);
                protocolHandler.addPartialResponse(stmId, new QicProtocolHandler.PartialResponse(h3headers));
            }
            else if (h3err == Http3.ErrorCode.TRANSPORT_ERROR) {
                throw new IOException("h3: send body failed: " + QuicheErrorCode.getMessage(qerr) + "(" + qerr + ")");
            }
            else {
                throw new IOException("h3: send body failed: " + H3ErrorCode.getMessage(h3err) + "(" + h3err + ")");
            }
        }
        else {
            BayLog.debug("%s stm#%d send header succeed cap=%d", tur, stmId, protocolHandler.con.streamCapacity(stmId));
            protocolHandler.postPackets();
        }
    }

    @Override
    public void sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendResContent len=%d posted=%d/%d", tur, stmId, len, tur.res.bytesPosted, tur.res.headers.contentLength());

        if(ofs > 0 || len  < bytes.length) {
            byte[] newBuf = new byte[len];
            System.arraycopy(bytes, ofs, newBuf, 0, len);
            bytes = newBuf;
        }

        QicProtocolHandler.PartialResponse part = null;
        if (protocolHandler.partialResponses.containsKey(stmId)) {
            BayLog.trace("%s stm#%d waiting. put packet into queue", tur, stmId, len);
            part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
        }
        else {
            int cap = protocolHandler.con.streamCapacity(stmId);
            BayLog.trace("%s stm#%d capacity=%d", tur, stmId, cap);

            if (cap < 0) {
                // Error
                if (cap == Quiche.ErrorCode.STREAM_STOPPED) {
                    BayLog.error("%s stm#%d Stream stopped", tur, stmId);
                    part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                }
                else {
                    throw protocolHandler.quicheError("Get capacity failed: ", (int)stmId, cap);
                }
            }
            else if (cap == 0) {
                part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
            }
            else {
                long written = protocolHandler.h3con.sendBody(tur.req.key, bytes, false);

                BayLog.debug("stm#%d send %d/%d bytes body", stmId, written, len);

                if (written < 0) {
                    // Error
                    if (written == Http3.ErrorCode.DONE) {
                        BayLog.debug("stm#%d send content DONE (^o^)", stmId);
                        part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
                    }
                    else if (written == Http3.ErrorCode.FRAME_UNEXPECTED) {
                        // Header not sent yet
                        part = new QicProtocolHandler.PartialResponse(bytes, 0, lis);
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
                        part = new QicProtocolHandler.PartialResponse(bytes, (int) written, lis);
                    }
                }
            }
        }

        if(part != null) {
            protocolHandler.addPartialResponse(stmId, part);
        }
        else {
            if (lis != null)
                lis.dataConsumed();
        }

        protocolHandler.postPackets();
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {

        long stmId = tur.req.key;
        BayLog.debug("%s stm#%d sendEndTour", tur, stmId);
        boolean retry = false;

        if (protocolHandler.partialResponses.containsKey(stmId)) {
            BayLog.debug("stm#%d put packet into que", stmId);
            retry = true;
        }
        else {

            int cap;
            long written = 0;

            cap = protocolHandler.con.streamCapacity(stmId);
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
                    throw protocolHandler.quicheError(tur + " stm#" + stmId + " Cannot get capacity: ", stmId, cap);
                }
            }
            else if (cap == 0) {
                BayLog.debug("%s stm#%d stream full, retry", tur, stmId);
                retry = true;
            }
            else {
                byte[] bytes = new byte[0];
                written = protocolHandler.h3con.sendBody(stmId, bytes, true);
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
                            throw protocolHandler.quicheError("h3: send body failed: ", stmId, qerr);
                        }
                        else {
                            throw protocolHandler.h3Error("h3: send body failed: ", stmId, h3err);
                        }
                    }
                }
            }
        }

        if(retry) {
            protocolHandler.addPartialResponse(stmId, new QicProtocolHandler.PartialResponse(true, lis));
        }
        else if (lis != null) {
            lis.dataConsumed();
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

            for (Http3Header hdr : cmd.reqHeaders) {
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
                int nRead = protocolHandler.h3con.recvBody(cmd.stmId, buf);

                if (nRead < 0) {
                    // Error
                    if (nRead == Http3.ErrorCode.DONE) {
                        //endReqContent(Tour.TOUR_ID_NOCHECK, tur);
                        break;
                    }
                    else {
                        BayLog.error("%s stm#%d h3: recv body failed :%s(%d)", this, cmd.stmId, H3ErrorCode.getMessage(nRead), nRead);
                        break;
                    }
                }
                else if (nRead == 0) {
                    break;
                }
                else {
                    int sid = protocolHandler.ship.shipId;
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

    @Override
    public void handleFinished(CmdFinished cmd) {
        BayLog.debug("%s stm#%d onFinished.", this, cmd.stmId);
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
        //sip.agent.shutdown(false);
        return;
    }

    private void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        // read shutdown
        BayLog.debug("%s endReqContent", tur);
        protocolHandler.con.streamShutdown(tur.req.key, Quiche.Shutdown.READ, 0L);
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
}
