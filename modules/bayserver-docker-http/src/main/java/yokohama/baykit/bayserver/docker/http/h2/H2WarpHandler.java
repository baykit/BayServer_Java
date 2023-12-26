package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.Town;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.docker.warp.WarpData;
import yokohama.baykit.bayserver.docker.warp.WarpHandler;
import yokohama.baykit.bayserver.docker.warp.WarpShip;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerFactory;

import java.io.IOException;

public class H2WarpHandler extends H2ProtocolHandler implements WarpHandler{

    public static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<H2Command, H2Packet, H2Type> {

        @Override
        public ProtocolHandler<H2Command, H2Packet, H2Type> createProtocolHandler(
                PacketStore<H2Packet, H2Type> pktStore) {
            return new H2WarpHandler(pktStore);
        }
    }

    final HeaderBlockAnalyzer analyzer = new HeaderBlockAnalyzer();
    int curStreamId = 1;

    protected H2WarpHandler(PacketStore<H2Packet, H2Type> pktStore) {
        super(pktStore, false);
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
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
        boolean available = tur.res.sendContent(Tour.TOUR_ID_NOCHECK, cmd.data, cmd.start, cmd.length);
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
            commandPacker.post(ship(), res);
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
        commandPacker.post(ship(), res);
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
    public void postWarpHeaders(Tour tur) throws IOException {
        sendReqHeaders(tur);
    }

    @Override
    public void postWarpContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendReqContents(tur, buf, start, len, lis);
    }

    @Override
    public void postWarpEnd(Tour tur) throws IOException {

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

    void sendReqHeaders(Tour tur) throws IOException {
        Town town = tur.town;

        //BayServer.debug(this + " construct header");
        String townPath = town.name();
        if (!townPath.endsWith("/"))
            townPath += "/";
        String newUri = ship().docker().warpBase + tur.req.uri.substring(townPath.length());

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
        commandPacker.post(ship(), cmdHdr);
    }


    void sendReqContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {

        CmdData cmd =
                new CmdData(
                        WarpData.get(tur).warpId,
                        len == 0 ? new H2Flags(H2Flags.FLAGS_END_STREAM) : null,
                        buf,
                        start,
                        len);
        commandPacker.post(ship, cmd, lis);
    }


    private void endResContent(Tour tur) throws IOException {
        tur.res.endContent(Tour.TOUR_ID_NOCHECK);
        ship().endWarpTour(tur);
    }

    WarpShip ship() {
        return (WarpShip) ship;
    }

}
