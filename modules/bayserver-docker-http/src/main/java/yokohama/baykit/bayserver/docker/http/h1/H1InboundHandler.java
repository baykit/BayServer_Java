package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.UpgradeException;
import yokohama.baykit.bayserver.docker.http.h2.H2ProtocolHandler;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.http.HtpDocker;
import yokohama.baykit.bayserver.docker.http.HtpPortDocker;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdEndContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdHeader;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import static yokohama.baykit.bayserver.docker.http.h1.H1InboundHandler.CommandState.*;

public class H1InboundHandler implements H1Handler, InboundHandler {

    public static class InboundProtocolHandlerFactory implements ProtocolHandlerFactory<H1Command, H1Packet, H1Type> {

        @Override
        public ProtocolHandler<H1Command, H1Packet, H1Type> createProtocolHandler(
                PacketStore<H1Packet, H1Type> pktStore) {
            H1InboundHandler inboundHandler = new H1InboundHandler();
            H1CommandUnPacker commandUnpacker = new H1CommandUnPacker(inboundHandler, true);
            H1PacketUnpacker packetUnpacker = new H1PacketUnpacker(commandUnpacker, pktStore);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            H1ProtocolHandler protocolHandler =
                    new H1ProtocolHandler(
                            inboundHandler,
                            packetUnpacker,
                            packetPacker,
                            commandUnpacker,
                            commandPacker,
                            true);
            inboundHandler.init(protocolHandler);
            return protocolHandler;
        }
    }


    enum CommandState {
        ReadHeader,
        ReadContent,
        Finished
    }

    H1ProtocolHandler protocolHandler;
    boolean headerRead;
    String httpProtocol;

    CommandState state;
    int curReqId = 1;
    Tour curTour;
    int curTourId;

    public H1InboundHandler() {
        resetState();
    }

    public void init(H1ProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        resetState();
        headerRead = false;
        httpProtocol = null;
        curReqId = 1;
        curTour = null;
        curTourId = 0;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements InboundHandler
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendHeaders(Tour tur) throws IOException {

        String resCon;

        // determine Connection header value
        if(tur.req.headers.getConnection() != Headers.ConnectionType.KeepAlive)
            // If client doesn't support "Keep-Alive", set "Close"
            resCon = "Close";
        else {
            resCon = "Keep-Alive";
            // Client supports "Keep-Alive"
            if (tur.res.headers.getConnection() != Headers.ConnectionType.KeepAlive) {
                // If tour doesn't need "Keep-Alive"
                if (tur.res.headers.contentLength() == -1) {
                    // If content-length not specified
                    if (tur.res.headers.contentType() != null &&
                            tur.res.headers.contentType().startsWith("text/")) {
                        // If content is text, connection must be closed
                        resCon = "Close";
                    }
                }
            }
        }

        tur.res.headers.set(Headers.CONNECTION, resCon);

        if(BayServer.harbor.traceHeader()) {
            BayLog.info("%s resStatus:%d", tur, tur.res.headers.status());
            tur.res.headers.headerNames().forEach(name ->
                    tur.res.headers.headerValues(name).forEach(value ->
                            BayLog.info("%s resHeader:%s=%s", tur, name, value)));
        }

        CmdHeader cmd = CmdHeader.newResHeader(tur.res.headers, tur.req.protocol);
        protocolHandler.post(cmd);
    }

    @Override
    public void sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        CmdContent cmd = new CmdContent(bytes, ofs, len);
        protocolHandler.post(cmd, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {
        InboundShip ship = ship();
        BayLog.trace("%s sendEndTour: tur=%s keep=%s", ship, tur, keepAlive);

        // Send end request command
        CmdEndContent cmd = new CmdEndContent();
        int sid = ship.shipId;
        Runnable ensureFunc = () -> {
            if(keepAlive && !ship.postman.isZombie()) {
                ship.keeping = true;
                ship.resumeRead(sid);
            }
            else
                ship.postman.postEnd();
        };

        try {
            protocolHandler.post(cmd, () -> {
                BayLog.debug("%s call back of end content command: tur=%s", ship, tur);
                ensureFunc.run();
                lis.dataConsumed();
            });
        }
        catch(IOException e) {
            ensureFunc.run();
            throw e;
        }

    }

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        BayLog.debug(e);
        Tour tur;
        if(curTour == null)
            tur = ship().getErrorTour();
        else
            tur = curTour;

        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.BAD_REQUEST, e.getMessage(), e);
        return true;
    }



    ////////////////////////////////////////////////////////////////////////////////
    // Implements H1CommandHandler
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction handleHeader(CmdHeader cmd) throws IOException {
        InboundShip sip = ship();
        BayLog.debug("%s handleHeader: method=%s uri=%s proto=%s", sip, cmd.method, cmd.uri, cmd.version);

        if (state == Finished)
            changeState(ReadHeader);

        if (state != ReadHeader || curTour != null) {
            String msg = "Header command not expected: state=" + state + " curTour=" + curTour;
            BayLog.error(msg);
            resetState();
            throw new ProtocolException(msg);
        }

        // check HTTP2
        String protocol = cmd.version.toUpperCase();
        if (protocol.equals("HTTP/2.0")) {
            HtpPortDocker port = (HtpPortDocker)sip.portDocker();
            if(port.supportH2) {
                sip.portDocker().returnProtocolHandler(sip.agentId, protocolHandler);
                H2ProtocolHandler protocolHandler = (H2ProtocolHandler)ProtocolHandlerStore.getStore(HtpDocker.H2_PROTO_NAME, true, sip.agentId).rent();
                sip.setProtocolHandler(protocolHandler);
                throw new UpgradeException();
            }
            else {
                throw new ProtocolException(
                        BayMessage.get(Symbol.HTP_UNSUPPORTED_PROTOCOL, protocol));
            }
        }

        Tour tur = sip.getTour(curReqId);
        if(tur == null) {
            BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
            tur = sip.getTour(curReqId, true);
            tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
            //sip.agent.shutdown(false);
            return NextSocketAction.Continue;
        }

        curTour = tur;
        curTourId = tur.tourId;
        curReqId++;  // issue new request id

        sip.keeping = false;

        this.httpProtocol = protocol;

        tur.req.uri = URLEncoder.encodeTilde(cmd.uri);
        tur.req.method = cmd.method.toUpperCase();
        tur.req.protocol = protocol;

        if (!(tur.req.protocol.equals("HTTP/1.1")
                || tur.req.protocol.equals("HTTP/1.0")
                || tur.req.protocol.equals("HTTP/0.9"))) {

            throw new ProtocolException(
                    BayMessage.get(Symbol.HTP_UNSUPPORTED_PROTOCOL, tur.req.protocol));
        }

        for(String[] nv: cmd.headers) {
            tur.req.headers.add(nv[0], nv[1]);
        }

        int reqContLen = tur.req.headers.contentLength();
        BayLog.debug("%s read header method=%s protocol=%s uri=%s contlen=%d",
                     sip, tur.req.method, tur.req.protocol, tur.req.uri, tur.req.headers.contentLength());

        if (BayServer.harbor.traceHeader()) {
            final Tour t = tur;
            cmd.headers.forEach( item ->
                BayLog.info(t + " h1: reqHeader: " + item[0] + "=" + item[1]));
        }

        if(reqContLen > 0) {
            tur.req.setLimit(reqContLen);
        }

        try {

            startTour(tur);

            if (reqContLen <= 0) {
                endReqContent(curTourId, tur);
                return NextSocketAction.Suspend; // end reading
            } else {
                changeState(ReadContent);
                return NextSocketAction.Continue;
            }

        } catch (HttpException e) {
            BayLog.debug(this + " Http error occurred: " + e);
            if(reqContLen <= 0) {
                // no post data
                tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);

                resetState(); // next: read empty stdin command
                return NextSocketAction.Continue;
            }
            else {
                // Delay send
                BayLog.trace(this + " error sending is delayed");
                changeState(ReadContent);
                tur.error = e;
                tur.req.setContentHandler(ReqContentHandler.devNull);
                return NextSocketAction.Continue;
            }
        }
    }

    @Override
    public NextSocketAction handleContent(CmdContent cmd) throws IOException {
        BayLog.debug("%s handleContent: len=%s", ship(), cmd.len);

        if (state != ReadContent) {
            CommandState s = state;
            resetState();
            throw new ProtocolException("Content command not expected: state=" + s);
        }

        Tour tur = curTour;
        int tourId = curTourId;
        int sid = ship().shipId;
        boolean success =
                tur.req.postContent(
                        tourId,
                        cmd.buffer,
                        cmd.start,
                        cmd.len,
                        (len, resume) -> {
                            if (resume)
                                tur.ship.resumeRead(sid);
                        });

        if (tur.req.bytesPosted == tur.req.bytesLimit) {
            if(tur.error != null){
                // Error has occurred on header completed
                tur.res.sendHttpException(tourId, tur.error);
                resetState();
                return NextSocketAction.Write;
            }
            else {
                try {
                    endReqContent(tourId, tur);
                    return NextSocketAction.Continue;
                } catch (HttpException e) {
                    tur.res.sendHttpException(tourId, e);
                    resetState();
                    return NextSocketAction.Write;
                }
            }
        }

        if(!success)
            return NextSocketAction.Suspend; // end reading
        else
            return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleEndContent(CmdEndContent cmd) {
        throw new Sink();
    }

    @Override
    public boolean reqFinished() {
        return state == Finished;
    }

    InboundShip ship() {
        return (InboundShip) protocolHandler.ship;
    }

    void endReqContent(int chkTurId, Tour tur) throws IOException, HttpException {
        tur.req.endContent(chkTurId);
        resetState();
    }

    void startTour(Tour tur) throws HttpException {
        boolean secure = ship().portDocker().secure();
        tur.req.parseHostPort(secure ? 443 : 80);
        tur.req.parseAuthorization();

        // Get remote address
        String clientAdr = tur.req.headers.get(Headers.X_FORWARDED_FOR);
        if (clientAdr != null) {
            tur.req.remoteAddress = clientAdr;
            tur.req.remotePort = -1;
        }
        else {
            try {
                Socket skt = ((SocketChannel) ship().ch).socket();
                tur.req.remotePort = skt.getPort();
                tur.req.remoteAddress = skt.getInetAddress().getHostAddress();
                tur.req.serverAddress = skt.getLocalAddress().getHostAddress();
            }
            catch(UnsupportedOperationException e) {
                // Unix domain socket
                tur.req.remotePort = -1;
                tur.req.remoteAddress = null;
                tur.req.serverAddress = null;
            }
        }
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req);

        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = secure;

        tur.go();
    }

    void changeState(CommandState newState) {
        state = newState;
    }

    void resetState() {
        headerRead = false;
        changeState(Finished);
        curTour = null;
    }
}
