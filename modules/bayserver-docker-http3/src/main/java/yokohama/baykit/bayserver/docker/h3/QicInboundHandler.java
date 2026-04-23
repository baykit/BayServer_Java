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
            protocolHandler.h3con.sendHeaders(stmId, h3headers, false);
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

            for (int i = 0; i == 0; i++) {
                byte[] buf;
                try {
                    buf = protocolHandler.h3con.recvBody(cmd.stmId);
                }
                catch(CrouteException e) {
                    BayLog.error("%s stm#%d h3: recv body failed :%s(%d)", this, cmd.stmId, H3ErrorCode.getMessage(e.code), e.code);
                    break;
                }

                if (buf == null) {
                    // DONE
                    break;
                }
                if (buf.length == 0) {
                    break;
                }

                int sid = protocolHandler.ship.shipId;
                int nRead = buf.length;
                boolean success =
                        tur.req.postReqContent(
                                Tour.TOUR_ID_NOCHECK,
                                buf,
                                0,
                                nRead,
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
}
