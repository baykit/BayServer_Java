package yokohama.baykit.bayserver.docker.ajp;

import baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgentMonitor;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.base.InboundHandler;
import baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.docker.base.InboundShip;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.HttpUtil;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;


public class AjpInboundHandler extends AjpProtocolHandler implements InboundHandler {

    static class InboundProtocolHandlerFactory implements ProtocolHandlerFactory<AjpCommand, AjpPacket, AjpType> {

        @Override
        public ProtocolHandler<AjpCommand, AjpPacket, AjpType> createProtocolHandler(
                PacketStore<AjpPacket, AjpType> pktStore) {
            return new AjpInboundHandler(pktStore);
        }
    }

    public enum CommandState {
        ReadForwardRequest,
        ReadData,
    }

    final static int DUMMY_KEY = 1;

    int curTourId;
    CmdForwardRequest reqCommand;

    CommandState state;
    boolean keeping;

    public AjpInboundHandler(PacketStore<AjpPacket, AjpType> pktStore) {
        super(pktStore, true);
        resetState();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        resetState();
        reqCommand = null;
        keeping = false;
        curTourId = 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements InboundHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendResHeaders(Tour tur) throws IOException {

        boolean chunked = false;
        CmdSendHeaders cmd = new CmdSendHeaders();
        for(String name : tur.res.headers.headerNames()) {
            for(String value : tur.res.headers.headerValues(name)) {
                cmd.addHeader(name, value);
            }
        }
        cmd.setStatus(tur.res.headers.status());
        commandPacker.post(ship, cmd);

        //BayLog.debug(this + " send header: content-length=" + tour.resHeaders.getContentLength());
    }

    @Override
    public void sendResContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        CmdSendBodyChunk cmd = new CmdSendBodyChunk(bytes, ofs, len);
        commandPacker.post(ship, cmd, lis);
    }

    @Override
    public void sendEndTour(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {

        BayLog.debug(ship + " endTour: tur=" + tur + " keep=" + keepAlive);
        CmdEndResponse cmd = new CmdEndResponse();
        cmd.reuse = keepAlive;

        Runnable ensureFunc = () -> {
            if (!keepAlive)
                commandPacker.end(ship);
        };

        try {
            commandPacker.post(ship, cmd, () -> {
                BayLog.debug(ship + " call back in sendEndTour: tur=" + tur + " keep=" + keepAlive);
                ensureFunc.run();
                lis.dataConsumed();
            });
        }
        catch(IOException e) {
            BayLog.debug(ship + " post failed in sendEndTour: tur=" + tur + " keep=" + keepAlive);
            ensureFunc.run();
            throw e;
        }
    }

    @Override
    public boolean sendReqProtocolError(ProtocolException e) throws IOException {
        InboundShip ibShip = (InboundShip)ship;
        Tour tur = ibShip.getErrorTour();
        tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.BAD_REQUEST, e.getMessage(), e);
        return true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements AjpCommandHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction handleForwardRequest(CmdForwardRequest cmd) throws IOException {
        InboundShip sip = ship();
        BayLog.debug("%s handleForwardRequest method=%s uri=%s", sip, cmd.method, cmd.reqUri);

        if(state != CommandState.ReadForwardRequest)
            throw new ProtocolException("Invalid AJP command: " + cmd.type);

        keeping = false;
        reqCommand = cmd;
        Tour tur = sip.getTour(DUMMY_KEY);
        if(tur == null) {
            BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
            tur = sip.getTour(DUMMY_KEY, true);
            tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
            tur.res.endContent(Tour.TOUR_ID_NOCHECK);
            sip.agent.shutdown();
            return NextSocketAction.Continue;
        }

        curTourId = tur.id();
        tur.req.uri = cmd.reqUri;
        tur.req.protocol = cmd.protocol;
        tur.req.method = cmd.method;
        cmd.headers.copyTo(tur.req.headers);

        String queryString = cmd.attributes.get("?query_string");
        if (StringUtil.isSet(queryString))
            tur.req.uri += "?" + queryString;

        BayLog.debug(tur + "%s read header method=%s protocol=%s uri=%s contlen=%d",
                 tur, tur.req.method, tur.req.protocol, tur.req.uri, tur.req.headers.contentLength());
        if (BayServer.harbor.traceHeader()) {
            for (String name : cmd.headers.headerNames()) {
                for(String value: cmd.headers.headerValues(name)) {
                    BayLog.info("%s header: %s=%s", tur, name, value);
                }
            }
        }

        int reqContLen = cmd.headers.contentLength();

        if(reqContLen > 0) {
            int sid = sip.shipId;
            tur.req.setConsumeListener(reqContLen, (len, resume) -> {
                if (resume)
                    sip.resume(sid);
            });
        }

        try {
            startTour(tur);

            if(reqContLen <= 0) {
                endReqContent(tur);
            }
            else {
                changeState(CommandState.ReadData);
            }
            return NextSocketAction.Continue;

        } catch (HttpException e) {
            if(reqContLen <= 0) {
                tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                //tur.zombie = true;
                resetState();
                return NextSocketAction.Write;
            }
            else {
                // Delay send
                changeState(CommandState.ReadData);
                tur.error = e;
                tur.req.setContentHandler(ReqContentHandler.devNull);
                return NextSocketAction.Continue;
            }
        }

    }

    @Override
    public NextSocketAction handleData(CmdData cmd) throws IOException {
        InboundShip sip = ship();
        BayLog.debug("%s handleData len=%s", sip, cmd.length);

        if(state != CommandState.ReadData)
            throw new ProtocolException("Invalid AJP command: " + cmd.type + " state=" + state);

        Tour tur = sip.getTour(DUMMY_KEY);
        boolean success = tur.req.postContent(Tour.TOUR_ID_NOCHECK, cmd.data, cmd.start, cmd.length);

        if(tur.req.bytesPosted == tur.req.bytesLimit) {
            // request content completed

            if(tur.error != null){
                // Error has occurred on header completed

                tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, tur.error);
                resetState();
                return NextSocketAction.Write;
            }
            else {
                try {
                    endReqContent(tur);
                    return NextSocketAction.Continue;
                } catch (HttpException e) {
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                    resetState();
                    return NextSocketAction.Write;
                }
            }
        }
        else {
            CmdGetBodyChunk bch = new CmdGetBodyChunk();
            bch.reqLen = tur.req.bytesLimit - tur.req.bytesPosted;
            if(bch.reqLen > AjpPacket.MAX_DATA_LEN) {
                bch.reqLen = AjpPacket.MAX_DATA_LEN;
            }
            commandPacker.post(sip, bch);

            if(!success)
                return NextSocketAction.Suspend;
            else
                return NextSocketAction.Continue;
        }
    }

    @Override
    public NextSocketAction handleEndResponse(CmdEndResponse cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleSendBodyChunk(CmdSendBodyChunk cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleSendHeaders(CmdSendHeaders cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleShutdown(CmdShutdown cmd) throws IOException {
        BayLog.debug(ship() + " handleShutdown");
        GrandAgentMonitor.shutdownAll();
        return NextSocketAction.Close;
    }

    @Override
    public NextSocketAction handleGetBodyChunk(CmdGetBodyChunk cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public boolean needData() {
        return state == CommandState.ReadData;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    void resetState() {
        changeState(CommandState.ReadForwardRequest);
    }

    void changeState(CommandState newState) {
        state = newState;
    }

    void endReqContent(Tour tur) throws IOException, HttpException {
        tur.req.endContent(Tour.TOUR_ID_NOCHECK);
        resetState();
    }


    void startTour(Tour tur) throws HttpException {
        HttpUtil.parseHostPort(tur, reqCommand.isSsl ? 443 : 80);
        HttpUtil.parseAuthrization(tur);

        Socket socket = ((SocketChannel)ship.ch).socket();
        tur.req.remotePort = -1;
        tur.req.remoteAddress = reqCommand.remoteAddr;
        tur.req.remoteHostFunc = () ->  reqCommand.remoteHost;

        tur.req.serverAddress = socket.getLocalAddress().getHostAddress();
        tur.req.serverPort = reqCommand.serverPort;
        tur.req.serverName = reqCommand.serverName;
        tur.isSecure = reqCommand.isSsl;

        tur.go();
    }

    InboundShip ship() {
        return (InboundShip) ship;
    }
}
