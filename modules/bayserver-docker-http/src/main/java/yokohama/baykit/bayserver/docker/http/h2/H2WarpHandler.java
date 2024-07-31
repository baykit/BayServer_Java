package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.Town;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.common.WarpData;
import yokohama.baykit.bayserver.common.WarpHandler;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;

public class H2WarpHandler implements WarpHandler, H2Handler {

    public static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<H2Command, H2Packet, H2Type> {

        @Override
        public ProtocolHandler<H2Command, H2Packet, H2Type> createProtocolHandler(
                PacketStore<H2Packet, H2Type> pktStore) {

            H2WarpHandler warpHandler = new H2WarpHandler();
            H2CommandUnPacker commandUnpacker = new H2CommandUnPacker(warpHandler);
            H2PacketUnPacker packetUnpacker = new H2PacketUnPacker(commandUnpacker, pktStore, false);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            H2ProtocolHandler protocolHandler =
                    new H2ProtocolHandler(warpHandler, packetUnpacker, packetPacker, commandUnpacker, commandPacker, false);
            warpHandler.init(protocolHandler);
            return protocolHandler;
        }
    }

    H2ProtocolHandler protocolHandler;
    final HeaderBlockAnalyzer analyzer = new HeaderBlockAnalyzer();
    public final HeaderTable reqHeaderTbl = HeaderTable.createDynamicTable();
    public final HeaderTable resHeaderTbl = HeaderTable.createDynamicTable();
    int curStreamId = 1;
    WarpShip ship;

    protected H2WarpHandler() {

    }

    private void init(H2ProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        curStreamId = 1;
    }

    /////////////////////////////////////
    // implements H2CommandHandler
    /////////////////////////////////////

    @Override
    public NextSocketAction handlePreface(CmdPreface cmd) throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public NextSocketAction handleData(CmdData cmd) throws IOException {
        Tour tur = ship().getTour(cmd.streamId);
        boolean available = tur.res.sendResContent(Tour.TOUR_ID_NOCHECK, cmd.data, cmd.start, cmd.length);
        if(!available)
            return NextSocketAction.Suspend;

        if (cmd.flags.endStream()) {
            endResContent(tur);
        }

        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleHeaders(CmdHeaders cmd) throws IOException {
        Tour tur = ship().getTour(cmd.streamId);
        WarpData wtur = WarpData.get(tur);

        if (tur.res.headerSent())
            throw new ProtocolException("Header command not expected");

        for(HeaderBlock blk : cmd.headerBlocks) {
            analyzer.clear();
            analyzer.analyzeHeaderBlock(blk, resHeaderTbl);
            if(BayLog.isTraceMode())
                BayLog.trace(ship() + " header block: " + blk + "(" + analyzer.name + "=" + analyzer.value + ")");
            if(analyzer.name != null) {
                if (analyzer.name.charAt(0) != ':') {
                    tur.res.headers.add(analyzer.name, analyzer.value);
                }
                else if (analyzer.status != null) {
                    try {
                        tur.res.headers.setStatus(Integer.parseInt(analyzer.status));
                    }
                    catch (NumberFormatException e) {
                        BayLog.error(e);
                    }
                }
                else {
                    throw new IllegalStateException();
                }
            }
        }

        if(cmd.flags.endHeaders()) {
            tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
            if (cmd.flags.endStream()) {
                endResContent(tur);
            }
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handlePriority(CmdPriority cmd) throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public NextSocketAction handleSettings(CmdSettings cmd) throws IOException {
        if(!cmd.flags.ack()){
            CmdSettings res = new CmdSettings(0, new H2Flags(H2Flags.FLAGS_ACK));
            protocolHandler.post(res);
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleWindowUpdate(CmdWindowUpdate cmd) throws IOException {
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleGoAway(CmdGoAway cmd) throws IOException {
        BayLog.error(ship() + " received GoAway: code=" + cmd.errorCode + " desc=" + H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode))
                + " debug=" + new String(cmd.debugData));
        ship().notifyServiceUnavailable("Received GoAway packet");
        return NextSocketAction.Close;
    }

    @Override
    public NextSocketAction handlePing(CmdPing cmd) throws IOException {
        CmdPing res = new CmdPing(cmd.streamId, new H2Flags(H2Flags.FLAGS_ACK), cmd.opaqueData);
        protocolHandler.post(res);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleRstStream(CmdRstStream cmd) throws IOException {
        Tour tur = ship().getTour(cmd.streamId);
        if(cmd.errorCode != H2ErrorCode.NO_ERROR) {
            BayLog.error(ship() + " received RstStream: code=" + cmd.errorCode +
                    " desc=" + H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode)));
            if (!tur.isValid()) {
                tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "Received GoAway packet");
            }
        }
        return NextSocketAction.Continue;
    }

    /////////////////////////////////////
    // implements WarpHandler
    /////////////////////////////////////
    @Override
    public int nextWarpId() {
        int cur = curStreamId;
        curStreamId += 2;
        return cur;
    }

    @Override
    public WarpData newWarpData(int warpId) {
        return null;
    }

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        sendReqHeaders(tur);
    }

    @Override
    public void sendContent(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendReqContents(tur, buf, start, len, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {

    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
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

    WarpShip ship() {
        return null;
    }

    void sendReqHeaders(Tour tur) throws IOException {
        Town town = tur.town;

        //BayServer.debug(this + " construct header");
        String townPath = town.name();
        if (!townPath.endsWith("/"))
            townPath += "/";
        String newUri = ship().docker().warpBase() + tur.req.uri.substring(townPath.length());

        CmdHeaders cmdHdr = new CmdHeaders(WarpData.get(tur).warpId);
        HeaderBlockBuilder bld = new HeaderBlockBuilder();
        HeaderBlock blk = bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_METHOD, tur.req.method, reqHeaderTbl);
        cmdHdr.addHeaderBlock(blk);

        blk = bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_PATH, newUri, reqHeaderTbl);
        cmdHdr.addHeaderBlock(blk);

        blk = bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_SCHEME, tur.isSecure ? "https" : "http", reqHeaderTbl);
        cmdHdr.addHeaderBlock(blk);

        blk = bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_AUTHORITY, tur.req.serverName, reqHeaderTbl);
        cmdHdr.addHeaderBlock(blk);

        for(String name: tur.req.headers.headerNames()) {
            if(!name.equalsIgnoreCase("connection")) {
                BayLog.trace("header: " + name);
                for (String value : tur.req.headers.headerValues(name)) {
                    blk = bld.buildHeaderBlock(name, value, reqHeaderTbl);
                    cmdHdr.addHeaderBlock(blk);
                }
            }
        }

        cmdHdr.flags = new H2Flags(H2Flags.FLAGS_END_HEADERS);
        ship().post(cmdHdr);
    }


    void sendReqContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {

        CmdData cmd =
                new CmdData(
                        WarpData.get(tur).warpId,
                        len == 0 ? new H2Flags(H2Flags.FLAGS_END_STREAM) : null,
                        buf,
                        start,
                        len);
        ship().post(cmd, lis);
    }


    private void endResContent(Tour tur) throws IOException {
        tur.res.endResContent(Tour.TOUR_ID_NOCHECK);
        ship.endWarpTour(tur, true);
    }

}
