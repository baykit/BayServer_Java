package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.WarpData;
import yokohama.baykit.bayserver.common.WarpHandler;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;


/**
 * AJP Protocol
 * https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 */
public class AjpWarpHandler implements WarpHandler, AjpHandler {

    static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<AjpCommand, AjpPacket, AjpType> {

        @Override
        public ProtocolHandler<AjpCommand, AjpPacket, AjpType> createProtocolHandler(
                PacketStore<AjpPacket, AjpType> pktStore) {
            AjpWarpHandler warpHandler = new AjpWarpHandler();
            AjpCommandUnPacker commandUnpacker = new AjpCommandUnPacker(warpHandler);
            AjpPacketUnPacker packetUnpacker = new AjpPacketUnPacker(pktStore, commandUnpacker);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            AjpProtocolHandler protocolHandler =
                    new AjpProtocolHandler(
                            warpHandler,
                            packetUnpacker,
                            packetPacker,
                            commandUnpacker,
                            commandPacker,
                            true);
            warpHandler.init(protocolHandler);
            return protocolHandler;
        }
    }

    public static int FIXED_WARP_ID = 1;

    enum CommandState {
        ReadHeader,
        ReadContent,
    }

    AjpProtocolHandler protocolHandler;
    CommandState state;
    int contReadLen;

    public AjpWarpHandler() {
        resetState();
    }

    private void init(AjpProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    @Override
    public String toString() {
        return ship().toString();
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        resetState();
        contReadLen = 0;
    }


    /////////////////////////////////////
    // Implements WarpHandler
    /////////////////////////////////////
    @Override
    public int nextWarpId() {
        return 1;
    }

    @Override
    public WarpData newWarpData(int warpId) {
        return new WarpData(ship(), warpId);
    }

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        sendForwardRequest(tur);
    }

    @Override
    public void sendContent(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendData(tur, buf, start, len, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {
        ship().post(null, lis);
    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
    }


    /////////////////////////////////////
    // Implements AjpCommandHandler
    /////////////////////////////////////

    @Override
    public NextSocketAction handleData(CmdData cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleEndResponse(CmdEndResponse cmd) throws IOException {
        BayLog.debug("%s handleEndResponse reuse=%b", this, cmd.reuse);
        WarpShip wsip = ship();
        Tour tur = wsip.getTour(FIXED_WARP_ID);

        if (state == CommandState.ReadHeader)
            endResHeader(tur);

        endResContent(tur);
        if(cmd.reuse)
            return NextSocketAction.Continue;
        else
            return NextSocketAction.Close;
    }

    @Override
    public NextSocketAction handleForwardRequest(CmdForwardRequest cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleSendBodyChunk(CmdSendBodyChunk cmd) throws IOException {
        BayLog.debug(this + " handleBodyChunk");
        WarpShip wsip = ship();
        Tour tur = wsip.getTour(FIXED_WARP_ID);

        if (state == CommandState.ReadHeader) {

            int sid = wsip.id();
            tur.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    wsip.resumeRead(sid);
                }
            });

            endResHeader(tur);
        }

        boolean available = tur.res.sendResContent(tur.tourId, cmd.chunk, 0, cmd.length);
        contReadLen += cmd.length;
        if(available)
            return NextSocketAction.Continue;
        else
            return NextSocketAction.Suspend;
    }

    @Override
    public NextSocketAction handleSendHeaders(CmdSendHeaders cmd) throws IOException {
        BayLog.debug(this + " handleSendHeaders");

        Tour tur = ship().getTour(FIXED_WARP_ID);

        if (state != CommandState.ReadHeader)
            throw new ProtocolException("Invalid AJP command: " + cmd.type + " state=" + state);

        WarpData wdata = WarpData.get(tur);

        if(BayServer.harbor.traceHeader())
            BayLog.info(wdata + " recv res status: " + cmd.status);
        wdata.resHeaders.setStatus(cmd.status);
        for (String name : cmd.headers.keySet()) {
            for (String value : cmd.headers.get(name)) {
                if(BayServer.harbor.traceHeader())
                    BayLog.info(wdata + " recv res header: " + name + "=" + value);
                wdata.resHeaders.add(name, value);
            }
        }

        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleShutdown(CmdShutdown cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleGetBodyChunk(CmdGetBodyChunk cmd) throws IOException {
        BayLog.debug(this + " handleGetBodyChunk");
        return NextSocketAction.Continue;
    }

    @Override
    public boolean needData() {
        return false;
    }

    /////////////////////////////////////
    // Implements ProtocolHandler
    /////////////////////////////////////
    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }


    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////

    void endResHeader(Tour tur) throws IOException {
        WarpData wdat = WarpData.get(tur);
        wdat.resHeaders.copyTo(tur.res.headers);
        tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
        changeState(CommandState.ReadContent);
    }

    void endResContent(Tour tur) throws IOException {
        ship().endWarpTour(tur);
        tur.res.endResContent(Tour.TOUR_ID_NOCHECK);
        resetState();
    }

    void changeState(CommandState newState) {
        state = newState;
    }

    void resetState() {
        changeState(CommandState.ReadHeader);
    }


    void sendForwardRequest(Tour tur) throws IOException {
        BayLog.debug(tur + " construct header");
        WarpShip wsip = ship();

        CmdForwardRequest cmd = new CmdForwardRequest();
        cmd.toServer = true;
        cmd.method = tur.req.method;
        cmd.protocol = tur.req.protocol;
        String relUri = tur.req.rewrittenURI != null ? tur.req.rewrittenURI : tur.req.uri;
        String twnPath = tur.town.name();
        if(!twnPath.endsWith("/"))
            twnPath += "/";
        relUri = relUri.substring(twnPath.length());
        String reqUri =  wsip.docker().warpBase() + relUri;

        int pos = reqUri.indexOf('?');
        if(pos >= 0) {
            cmd.reqUri = reqUri.substring(0, pos);
            cmd.attributes.put("?query_string", reqUri.substring(pos + 1));
        }
        else {
            cmd.reqUri = reqUri;
        }
        cmd.remoteAddr = tur.req.remoteAddress;
        cmd.remoteHost = tur.req.remoteHost();
        cmd.serverName = tur.req.serverName;
        cmd.serverPort = tur.req.serverPort;
        cmd.isSsl = tur.isSecure;
        tur.req.headers.copyTo(cmd.headers);
        //cmd.headers.setHeader(Headers.HOST, docker.host + ":" + docker.port);
        //cmd.headers.setHeader(Headers.CONNECTION, "keep-alive");
        cmd.serverPort =  wsip.docker().port();

        if(BayServer.harbor.traceHeader()) {
            cmd.headers.headerNames().forEach(name -> {
                cmd.headers.headerValues(name).forEach(value -> {
                    BayLog.info("%s sendWarpHeader: %s=%s", WarpData.get(tur), name, value);
                });
            });
        }
        ship().post(cmd);
    }

    void sendData(Tour tur, byte[] data, int ofs, int len, DataConsumeListener lis) throws IOException {
        BayLog.debug("%s construct contents", tur);
        WarpShip wsip = ship();

        CmdData cmd = new CmdData(data, ofs, len);
        cmd.toServer = true;
        ship().post(cmd, lis);
    }

    WarpShip ship() {
        return (WarpShip) protocolHandler.ship;
    }
}
