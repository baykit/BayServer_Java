package baykit.bayserver.docker.ajp;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.*;
import baykit.bayserver.docker.warp.WarpShip;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.ajp.command.*;
import baykit.bayserver.docker.warp.*;
import baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;

import static baykit.bayserver.agent.NextSocketAction.*;


/**
 * AJP Protocol
 * https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 */
public class AjpWarpHandler extends AjpProtocolHandler implements WarpHandler {

    static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<AjpCommand, AjpPacket, AjpType> {

        @Override
        public ProtocolHandler<AjpCommand, AjpPacket, AjpType> createProtocolHandler(
                PacketStore<AjpPacket, AjpType> pktStore) {
            return new AjpWarpHandler(pktStore);
        }
    }

    public static int FIXED_WARP_ID = 1;

    enum CommandState {
        ReadHeader,
        ReadContent,
    }

    CommandState state;
    int contReadLen;

    public AjpWarpHandler(PacketStore<AjpPacket, AjpType> pktStore) {
        super(pktStore, false);
        resetState();
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    /////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        resetState();
        contReadLen = 0;
    }


    /////////////////////////////////////////////////////////////////////////////////
    // Implements WarpHandler
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    public int nextWarpId() {
        return 1;
    }

    @Override
    public WarpData newWarpData(int warpId) {
        return new WarpData(ship(), warpId);
    }

    @Override
    public void postWarpHeaders(Tour tur) throws IOException {
        sendForwardRequest(tur);
    }

    @Override
    public void postWarpContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendData(tur, buf, start, len, lis);
    }

    @Override
    public void postWarpEnd(Tour tur) throws IOException {

    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
    }


    /////////////////////////////////////////////////////////////////////////////////
    // Implements AjpCommandHandler
    /////////////////////////////////////////////////////////////////////////////////

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
            return Continue;
        else
            return Close;
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
                    wsip.resume(sid);
                }
            });

            endResHeader(tur);
        }

        boolean available = tur.res.sendContent(tur.tourId, cmd.chunk, 0, cmd.length);
        contReadLen += cmd.length;
        if(available)
            return Continue;
        else
            return Suspend;
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

        return Continue;
    }

    @Override
    public NextSocketAction handleShutdown(CmdShutdown cmd) throws IOException {
        throw new ProtocolException("Invalid AJP command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleGetBodyChunk(CmdGetBodyChunk cmd) throws IOException {
        BayLog.debug(this + " handleGetBodyChunk");
        return Continue;
    }

    @Override
    public boolean needData() {
        return false;
    }

    /////////////////////////////////////////////////////////////////////////////////
    // Custom methods
    /////////////////////////////////////////////////////////////////////////////////

    void endResHeader(Tour tur) throws IOException {
        WarpData wdat = WarpData.get(tur);
        wdat.resHeaders.copyTo(tur.res.headers);
        tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
        changeState(CommandState.ReadContent);
    }

    void endResContent(Tour tur) throws IOException {
        ship().endWarpTour(tur);
        tur.res.endContent(Tour.TOUR_ID_NOCHECK);
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
        String reqUri =  wsip.docker().warpBase + relUri;

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
        cmd.serverPort =  wsip.docker().port;

        if(BayServer.harbor.traceHeader()) {
            cmd.headers.headerNames().forEach(name -> {
                cmd.headers.headerValues(name).forEach(value -> {
                    BayLog.info("%s sendWarpHeader: %s=%s", WarpData.get(tur), name, value);
                });
            });
        }
        commandPacker.post(wsip, cmd);
    }

    void sendData(Tour tur, byte[] data, int ofs, int len, DataConsumeListener lis) throws IOException {
        BayLog.debug("%s construct contents", tur);
        WarpShip wsip = ship();

        CmdData cmd = new CmdData(data, ofs, len);
        cmd.toServer = true;
        commandPacker.post(wsip, cmd, lis);
    }

    WarpShip ship() {
        return (WarpShip) ship;
    }
}
