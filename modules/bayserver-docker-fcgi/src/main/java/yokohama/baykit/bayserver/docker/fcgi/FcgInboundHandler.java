package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.docker.fcgi.command.*;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static yokohama.baykit.bayserver.docker.fcgi.FcgInboundHandler.CommandState.*;

public class FcgInboundHandler implements InboundHandler, FcgHandler {

    static class InboundProtocolHandlerFactory implements ProtocolHandlerFactory<FcgCommand, FcgPacket, FcgType> {

        @Override
        public ProtocolHandler<FcgCommand, FcgPacket, FcgType> createProtocolHandler(
                PacketStore<FcgPacket, FcgType> pktStore) {
            FcgInboundHandler inboundHandler = new FcgInboundHandler();
            FcgCommandUnPacker commandUnpacker = new FcgCommandUnPacker(inboundHandler);
            FcgPacketUnPacker packetUnpacker = new FcgPacketUnPacker(commandUnpacker, pktStore);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            FcgProtocolHandler protocolHandler =
                    new FcgProtocolHandler(
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

    static String HDR_HTTP_CONNECTION = "HTTP_CONNECTION";

    enum CommandState {
        ReadBeginRequest,
        ReadParams,
        ReadStdIn,
    }

    CommandState state;
    FcgProtocolHandler protocolHandler;

    Map<String, String> env = new HashMap<>();
    int reqId;
    boolean reqKeepAlive;

    public FcgInboundHandler() {
        resetState();
    }

    private void init(FcgProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }


    @Override
    public String toString() {
        return ClassUtil.getLocalName(getClass());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Override methods in Ship
    ///////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void reset() {
        env.clear();
        resetState();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements InboundHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendHeaders(Tour tur) throws IOException {

        BayLog.debug(ship() + " PH:sendHeaders: tur=" + tur);

        int scode = tur.res.headers.status();
        String status = scode + " " + HttpStatus.description(scode);
        tur.res.headers.set(Headers.STATUS, status);

        if(BayServer.harbor.traceHeader()) {
            BayLog.info(tur + " resStatus:" + tur.res.headers.status());
            tur.res.headers.headerNames().forEach(name ->
                    tur.res.headers.headerValues(name).forEach(value ->
                            BayLog.info(tur + " resHeader:" + name + "=" + value)));
        }

        ByteArrayOutputStream hout = new ByteArrayOutputStream();
        HttpUtil.sendMimeHeaders(tur.res.headers, hout);
        HttpUtil.sendNewLine(hout);
        byte[] data = hout.toByteArray();
        FcgCommand cmd = new CmdStdOut(tur.req.key, data, 0, data.length);
        protocolHandler.post(cmd);
    }

    @Override
    public void sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        CmdStdOut cmd = new CmdStdOut(tur.req.key, bytes, ofs, len);
        protocolHandler.post(cmd, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {

        BayLog.debug("%s PH:endTour: tur=%s keep=%s", ship(), tur, keepAlive);

        // Send empty stdout command
        FcgCommand cmd = new CmdStdOut(tur.req.key);
        protocolHandler.post(cmd);

        // Send end request command
        cmd = new CmdEndRequest(tur.req.key);
        Runnable ensureFunc = () -> {
            if(!keepAlive)
                ship().postClose();
        };

        try {
            protocolHandler.post(cmd, () -> {
                BayLog.debug("%s call back in sendEndTour: tur=%s keep=%b", ship(), tur, keepAlive);
                ensureFunc.run();
                lis.dataConsumed();
            });
        }
        catch(IOException e) {
            BayLog.debug("%s post faile in sendEndTour: tur=%s keep=%b", ship(), tur, keepAlive);
            ensureFunc.run();
            throw e;
        }
    }

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        BayLog.debug(e);
        InboundShip ibShip = ship();
        Tour tur = ibShip.getErrorTour();
        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.BAD_REQUEST, e.getMessage(), e);
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Override methods in FcgCommandHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public NextSocketAction handleBeginRequest(CmdBeginRequest cmd) throws IOException {
        InboundShip sip = ship();
        if (BayLog.isDebugMode())
            BayLog.debug(sip + " handleBeginRequest reqId=" + cmd.reqId + " keep=" + cmd.keepConn);

        if(state != ReadBeginRequest)
            throw new ProtocolException("fcgi: Invalid command: " + cmd.type + " state=" + state);

        checkReqId(cmd.reqId);

        reqId = cmd.reqId;
        Tour tur = sip.getTour(cmd.reqId);
        if(tur == null) {
            BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
            tur = sip.getTour(cmd.reqId, true);
            tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
            //sip.agent.shutdown();
            return NextSocketAction.Continue;
        }

        reqKeepAlive = cmd.keepConn;

        changeState(ReadParams);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleEndRequest(CmdEndRequest cmd) throws IOException {
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleParams(CmdParams cmd) throws IOException {
        InboundShip sip = ship();
        if (BayLog.isDebugMode())
            BayLog.debug(sip + " handleParams reqId=" + cmd.reqId + " nParams=" + cmd.params.size());

        if(state != ReadParams)
            throw new ProtocolException("fcgi: Invalid command: " + cmd.type + " state=" + state);

        checkReqId(cmd.reqId);

        Tour tur = sip.getTour(cmd.reqId);

        if(cmd.params.isEmpty()) {
            // Header completed

            // check keep-alive
            //  keep-alive flag of BeginRequest has high priority
            if (reqKeepAlive) {
                if (!tur.req.headers.contains(Headers.CONNECTION))
                    tur.req.headers.set(Headers.CONNECTION, "Keep-Alive");
            }
            else {
                tur.req.headers.set(Headers.CONNECTION, "Close");
            }

            int reqContLen = tur.req.headers.contentLength();

            // end params
            if (BayLog.isDebugMode())
                BayLog.debug(tur + " read header method=" + tur.req.method + " protocol=" + tur.req.protocol + " uri=" + tur.req.uri + " contlen=" + reqContLen);
            if (BayServer.harbor.traceHeader()) {
                for (String name : tur.req.headers.headerNames()) {
                    for(String value: tur.req.headers.headerValues(name)) {
                        BayLog.info("%s  reqHeader: %s=%s", tur, name, value);
                    }
                }
            }

            if(reqContLen > 0) {
                tur.req.setLimit(reqContLen);
            }

            changeState(ReadStdIn);
            try {
                startTour(tur);

                return NextSocketAction.Continue;

            } catch (HttpException e) {
                BayLog.debug(this + " Http error occurred: " + e);
                if(reqContLen <= 0) {
                    // no post data
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);

                    changeState(ReadStdIn); // next: read empty stdin command
                    return NextSocketAction.Continue;
                }
                else {
                    // Delay send
                    changeState(ReadStdIn);
                    tur.error = e;
                    tur.req.setReqContentHandler(ReqContentHandler.devNull);
                    return NextSocketAction.Continue;
                }
            }
        }
        else {
            if (BayServer.harbor.traceHeader()) {
                BayLog.info("%s Read FcgiParam", tur);
            }
            for (String[] nv : cmd.params) {
                String name = nv[0];
                String value = nv[1];
                if (BayServer.harbor.traceHeader()) {
                    BayLog.info("%s  param: %s=%s", tur, name, value);
                }
                env.put(name, value);

                if (name.startsWith("HTTP_")) {
                    String hname = name.substring(5);
                    tur.req.headers.add(hname, value);
                } else if (name.equals("CONTENT_TYPE")) {
                    tur.req.headers.add(Headers.CONTENT_TYPE, value);
                } else if (name.equals("CONTENT_LENGTH")) {
                    tur.req.headers.add(Headers.CONTENT_LENGTH, value);
                } else if (name.equals("HTTPS")) {
                    tur.isSecure = value.toLowerCase().equals("on");
                }
            }

            tur.req.uri = env.get("REQUEST_URI");
            tur.req.protocol = env.get("SERVER_PROTOCOL");
            tur.req.method = env.get("REQUEST_METHOD");

            if (BayLog.isDebugMode())
                BayLog.debug(sip + " read params method=" + tur.req.method + " protocol=" + tur.req.protocol + " uri=" + tur.req.uri + " contlen=" + tur.req.headers.contentLength());

            return NextSocketAction.Continue;
        }
    }

    @Override
    public NextSocketAction handleStdErr(CmdStdErr cmd) throws IOException{
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleStdIn(CmdStdIn cmd) throws IOException {
        InboundShip sip = ship();
        if (BayLog.isDebugMode())
            BayLog.debug(sip + " handleStdIn reqId=" + cmd.reqId + " len=" + cmd.length);

        if(state != ReadStdIn)
            throw new ProtocolException("fcgi: Invalid FCGI command: " + cmd.type + " state=" + state);

        checkReqId(cmd.reqId);

        Tour tur = sip.getTour(cmd.reqId);
        if(cmd.length == 0) {
            // request content completed

            if(tur.error != null){
                // Error has occurred on header completed

                tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, tur.error);
                resetState();
                return NextSocketAction.Write;
            }
            else {
                try {
                    endReqContent(Tour.TOUR_ID_NOCHECK, tur);
                    return NextSocketAction.Continue;
                } catch (HttpException e) {
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                    return NextSocketAction.Write;
                }
            }
        }
        else {
            int sid = ship().shipId;
            boolean success =
                    tur.req.postReqContent(
                            Tour.TOUR_ID_NOCHECK,
                            cmd.data,
                            cmd.start,
                            cmd.length,
                            (len, resume) -> {
                                if (resume)
                                    sip.resumeRead(sid);
                            });

            if (!success)
                return NextSocketAction.Suspend;
            else
                return NextSocketAction.Continue;
        }
    }

    @Override
    public NextSocketAction handleStdOut(CmdStdOut cmd) throws ProtocolException {
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    void resetState() {
	    //BayLog.debug(this + " resetState");
        changeState(ReadBeginRequest);
        reqId = FcgPacket.FCGI_NULL_REQUEST_ID;
    }

    void checkReqId(int receivedId) throws IOException {
        if(receivedId == FcgPacket.FCGI_NULL_REQUEST_ID)
            throw new ProtocolException("Invalid request id: " + receivedId);

        if(reqId == FcgPacket.FCGI_NULL_REQUEST_ID)
            reqId = receivedId;

        if(reqId != receivedId) {
            BayLog.error(ship() + " invalid request id: received=" + receivedId + " reqId=" + reqId);
            throw new ProtocolException("Invalid request id: " + receivedId);
        }
    }

    void changeState(CommandState newState) {
        state = newState;
    }

    void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        tur.req.endReqContent(checkId);
        resetState();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    void startTour(Tour tur) throws HttpException {
        //String scheme = env.get(CGIUtil.REQUEST_SCHEME);
        //ShipUtil.parseHostPort(tour, scheme == null ? false : scheme.equalsIgnoreCase("https"));
        tur.req.parseHostPort(tur.isSecure ? 443 : 80);
        tur.req.parseAuthorization();

        try {
            tur.req.remotePort = Integer.parseInt(env.get(CGIUtil.REMOTE_PORT));
        }
        catch(Exception e) {
            BayLog.error(e);
        }
        tur.req.remoteAddress = env.get(CGIUtil.REMOTE_ADDR);
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req.remoteAddress);

        tur.req.serverName = env.get(CGIUtil.SERVER_NAME);
        tur.req.serverAddress = env.get(CGIUtil.SERVER_ADDR);
        try {
            tur.req.serverPort = Integer.parseInt(env.get(CGIUtil.SERVER_PORT));
        }
        catch(Exception e) {
            BayLog.error(e);
            tur.req.serverPort = 80;
        }

        tur.go();
    }

    InboundShip ship() {
        return (InboundShip)protocolHandler.ship;
    }
}
