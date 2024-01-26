package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.tour.TourStore;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class H2InboundHandler implements H2Handler, InboundHandler {

    public static class InboundProtocolHandlerFactory implements ProtocolHandlerFactory<H2Command, H2Packet, H2Type> {

        @Override
        public ProtocolHandler<H2Command, H2Packet, H2Type> createProtocolHandler(
                PacketStore<H2Packet, H2Type> pktStore) {

            H2InboundHandler inboundHandler = new H2InboundHandler();
            H2CommandUnPacker commandUnpacker = new H2CommandUnPacker(inboundHandler);
            H2PacketUnPacker packetUnpacker = new H2PacketUnPacker(commandUnpacker, pktStore, true);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            H2ProtocolHandler protocolHandler =
                    new H2ProtocolHandler(inboundHandler, packetUnpacker, packetPacker, commandUnpacker, commandPacker, true);
            inboundHandler.init(protocolHandler);
            return protocolHandler;
        }
    }

    H2ProtocolHandler protocolHandler;
    boolean headerRead;
    String httpProtocol;

    int reqContLen;
    int reqContRead;
    int windowSize = BayServer.harbor.tourBufferSize();
    final H2Settings settings = new H2Settings();
    final HeaderBlockAnalyzer analyzer = new HeaderBlockAnalyzer();
    public final HeaderTable reqHeaderTbl = HeaderTable.createDynamicTable();
    public final HeaderTable resHeaderTbl = HeaderTable.createDynamicTable();



    public H2InboundHandler() {

    }

    public void init(H2ProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements Reusable
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        headerRead = false;

        reqContLen = 0;
        reqContRead = 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements InboundHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        CmdHeaders cmd = new CmdHeaders(tur.req.key);

        HeaderBlockBuilder bld = new HeaderBlockBuilder();

        HeaderBlock blk = bld.buildHeaderBlock(":status", Integer.toString(tur.res.headers.status()), resHeaderTbl);
        cmd.headerBlocks.add(blk);

        // headers
        if(BayServer.harbor.traceHeader())
            BayLog.info("%s H2 res status: %d", tur, tur.res.headers.status());
        for (String name : tur.res.headers.headerNames()) {
            if(name.equalsIgnoreCase("connection")) {
                BayLog.trace("%s Connection header is discarded", tur);
            }
            else {
                Iterator<String> values = tur.res.headers.headerValues(name).iterator();
                //name = name.substring(0, 1).toUpperCase() + name.substring(1);
                while (values.hasNext()) {
                    String value = values.next();
                    if (BayServer.harbor.traceHeader())
                        BayLog.info("%s H2 res header: %s=%s", tur, name, value);
                    blk = bld.buildHeaderBlock(name, value, resHeaderTbl);
                    cmd.headerBlocks.add(blk);
                }
            }
        }

        cmd.flags.setEndHeaders(true);
        cmd.excluded = false;
        // cmd.streamDependency = streamId;
        cmd.flags.setPadded(false);

        protocolHandler.post(cmd);
    }

    @Override
    public void sendContent(Tour tur, byte[] bytes, int ofs, final int len, DataConsumeListener lis) throws IOException {
        CmdData cmd = new CmdData(tur.req.key, null, bytes, ofs, len);
        protocolHandler.post(cmd, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {
        CmdData cmd = new CmdData(tur.req.key, null, new byte[0], 0, 0);
        cmd.flags.setEndStream(true);
        protocolHandler.post(cmd, lis);
    }

    @Override
    public boolean onProtocolError(ProtocolException e) {
        BayLog.debug(e);
        BayLog.error(e, e.getMessage());
        CmdGoAway cmd = new CmdGoAway(H2ProtocolHandler.CTL_STREAM_ID);
        cmd.streamId = 0;
        cmd.lastStreamId = 0;
        cmd.errorCode = H2ErrorCode.PROTOCOL_ERROR;
        cmd.debugData = "Thank you!".getBytes(StandardCharsets.UTF_8);
        try {
            protocolHandler.post(cmd);
            protocolHandler.ship.postClose();
        }
        catch(IOException ex) {
            BayLog.error(ex);
        }
        return false;
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements H2CommandHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction handlePreface(CmdPreface cmd) throws IOException {
        InboundShip sip = ship();
        BayLog.debug("%s h2: handle_preface: proto=%s", sip, cmd.protocol);

        httpProtocol = cmd.protocol;

        CmdSettings set = new CmdSettings(H2ProtocolHandler.CTL_STREAM_ID);
        set.streamId = 0;
        set.items.add(new CmdSettings.Item(CmdSettings.MAX_CONCURRENT_STREAMS, TourStore.MAX_TOURS));
        set.items.add(new CmdSettings.Item(CmdSettings.INITIAL_WINDOW_SIZE, windowSize));
        protocolHandler.post(set);

        set = new CmdSettings(H2ProtocolHandler.CTL_STREAM_ID);
        set.streamId = 0;
        set.flags.setAck(true);
        //cmdPacker.send(set);

        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleHeaders(CmdHeaders cmd) throws IOException {
        BayLog.debug("%s handle_headers: stm=%d dep=%d weight=%d", ship(), cmd.streamId, cmd.streamDependency, cmd.weight);
        Tour tur = getTour(cmd.streamId);
        if(tur == null) {
            BayLog.error(BayMessage.get(Symbol.INT_NO_MORE_TOURS));
            tur = ship().getTour(cmd.streamId, true);
            tur.res.sendError(Tour.TOUR_ID_NOCHECK, HttpStatus.SERVICE_UNAVAILABLE, "No available tours");
            //sip.agent.shutdown(false);
            return NextSocketAction.Continue;
        }

        for(HeaderBlock blk : cmd.headerBlocks) {
            if(blk.op == HeaderBlock.HeaderOp.UpdateDynamicTableSize) {
                BayLog.trace("%s header block update table size: %d", tur, blk.size);
                reqHeaderTbl.setSize(blk.size);
                continue;
            }

            analyzer.analyzeHeaderBlock(blk, reqHeaderTbl);
            if(BayServer.harbor.traceHeader())
                BayLog.info("%s req header: %s=%s :%s", tur, analyzer.name, analyzer.value, blk);

            if(analyzer.name == null) {
                continue;
            }
            else if(analyzer.name.charAt(0) != ':') {
                tur.req.headers.add(analyzer.name, analyzer.value);
            }
            else if(analyzer.method != null) {
                tur.req.method = analyzer.method;
            }
            else if(analyzer.path != null) {
                tur.req.uri = analyzer.path;
            }
            else if(analyzer.scheme != null) {
            }
            else if(analyzer.status != null) {
                throw new IllegalStateException();
            }
        }

        if (cmd.flags.endHeaders()) {
            tur.req.protocol = "HTTP/2.0";
            BayLog.debug("%s H2 read header method=%s protocol=%s uri=%s contlen=%d",
                            ship(), tur.req.method, tur.req.protocol, tur.req.uri, tur.req.headers.contentLength());

            int reqContLen = tur.req.headers.contentLength();

            if(reqContLen > 0) {
                tur.req.setLimit(reqContLen);
            }

            try {
                startTour(tur);
                if (tur.req.headers.contentLength() <= 0) {
                    endReqContent(tur.id(), tur);
                }
            } catch (HttpException e) {
                BayLog.debug("%s Http error occurred: %s", this, e);
                if(reqContLen <= 0) {
                    // no post data
                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);

                    return NextSocketAction.Continue;
                }
                else {
                    // Delay send
                    tur.error = e;
                    tur.req.setContentHandler(ReqContentHandler.devNull);
                    return NextSocketAction.Continue;
                }
            }
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleData(CmdData cmd) throws IOException {
        BayLog.debug("%s handle_data: stm=%d len=%d", ship(), cmd.streamId, cmd.length);
        Tour tur = getTour(cmd.streamId);
        if(tur == null) {
            throw new IllegalArgumentException("Invalid stream id: " + cmd.streamId);
        }
        if(tur.req.headers.contentLength() <= 0) {
            throw new ProtocolException("Post content not allowed");
        }

        boolean success = true;
        if(cmd.length > 0) {
            int tid = tur.tourId;
            success =
                    tur.req.postContent(
                            Tour.TOUR_ID_NOCHECK,
                            cmd.data,
                            cmd.start,
                            cmd.length,
                            (len, resume) -> {
                                tur.checkTourId(tid);

                                if (len > 0) {
                                    CmdWindowUpdate upd = new CmdWindowUpdate(cmd.streamId);
                                    upd.windowSizeIncrement = len;
                                    CmdWindowUpdate upd2 = new CmdWindowUpdate(0);
                                    upd2.windowSizeIncrement = len;
                                    CommandPacker cmdPacker = protocolHandler.commandPacker;
                                    try {
                                        protocolHandler.post(upd);
                                        protocolHandler.post(upd2);
                                    }
                                    catch(IOException e) {
                                        BayLog.error(e);
                                    }
                                }

                                if (resume)
                                    tur.ship.resumeRead(tur.shipId);
                            });

            if (tur.req.bytesPosted >= tur.req.headers.contentLength()) {

                if(tur.error != null){
                    // Error has occurred on header completed

                    tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, tur.error);
                    return NextSocketAction.Continue;
                }
                else {
                    try {
                        endReqContent(tur.id(), tur);
                    } catch (HttpException e) {
                        tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
                        return NextSocketAction.Continue;
                    }
                }
            }
        }

        if(!success)
            return NextSocketAction.Suspend;
        else
            return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handlePriority(CmdPriority cmd) throws IOException {
        if(cmd.streamId == 0)
            throw new ProtocolException("Invalid streamId");
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleSettings(CmdSettings cmd) throws IOException {
        BayLog.debug("%s handleSettings: stmid=%d", ship(), cmd.streamId);
        if(cmd.flags.ack())
            return NextSocketAction.Continue; // ignore ACK

        for(CmdSettings.Item item : cmd.items) {
            BayLog.debug("%s handle: Setting id=%d, value=%d", ship(), item.id, item.value);
            switch(item.id) {
                case CmdSettings.HEADER_TABLE_SIZE:
                    settings.headerTableSize = item.value;
                    break;
                case CmdSettings.ENABLE_PUSH:
                    settings.enablePush = (item.value != 0);
                    break;
                case CmdSettings.MAX_CONCURRENT_STREAMS:
                    settings.maxConcurrentStreams = item.value;
                    break;
                case CmdSettings.INITIAL_WINDOW_SIZE:
                    settings.initialWindowSize = item.value;;
                    break;
                case CmdSettings.MAX_FRAME_SIZE:
                    settings.maxFrameSize = item.value;
                    break;
                case CmdSettings.MAX_HEADER_LIST_SIZE:
                    settings.maxHeaderListSize = item.value;
                    break;
                default:
                    BayLog.debug("Invalid settings id (Ignore): %d", item.id);
            }
        }

        CmdSettings res = new CmdSettings(0, new H2Flags(H2Flags.FLAGS_ACK));
        protocolHandler.post(res);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleWindowUpdate(CmdWindowUpdate cmd) throws IOException {
        if(cmd.windowSizeIncrement == 0)
            throw new ProtocolException("Invalid increment value");
        BayLog.debug("%s handleWindowUpdate: stmid=%d siz=%d", ship(),  cmd.streamId, cmd.windowSizeIncrement);
        int windowSizse = cmd.windowSizeIncrement;
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleGoAway(CmdGoAway cmd) throws IOException {
        BayLog.debug("%s received GoAway: lastStm=%d code=%d desc=%s debug=%s",
                ship(), cmd.lastStreamId, cmd.errorCode, H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode)), new String(cmd.debugData));
        return NextSocketAction.Close;

        /*
        CmdGoAway awy = new CmdGoAway(H2ProtocolHandler.CTL_STREAM_ID);
        awy.lastStreamId = cmd.lastStreamId + 1;
        awy.errorCode = H2ErrorCode.NO_ERROR;
        awy.debugData = "Thank you!".getBytes(StandardCharsets.UTF_8);
        H2CommandPacker cmdPacker = h2ProtocolHandler().commandPacker();

        cmdPacker.send(cmd);
        cmdPacker.sendEnd(Closing);
        return Writing;
        */
    }

    @Override
    public NextSocketAction handlePing(CmdPing cmd) throws IOException {
        InboundShip sip = ship();
        BayLog.debug("%s handle_ping: stm=%d", sip, cmd.streamId);

        CmdPing res = new CmdPing(cmd.streamId, new H2Flags(H2Flags.FLAGS_ACK), cmd.opaqueData);
        protocolHandler.post(res);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleRstStream(CmdRstStream cmd) throws IOException {
        BayLog.debug("%s received RstStream: stmid=%d code=%d desc=%s",
                ship(), cmd.streamId, cmd.errorCode, H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode)));
        return NextSocketAction.Continue;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // private
    ///////////////////////////////////////////////////////////////////////////////////////////
    InboundShip ship() {
        return (InboundShip) protocolHandler.ship;
    }

    Tour getTour(int key) {
        return ship().getTour(key);
    }

    private void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        tur.req.endContent(checkId);
    }

    void startTour(Tour tur) throws HttpException {
        InboundShip sip = ship();

        tur.req.parseHostPort(sip.portDocker().secure() ? 443 : 80);
        tur.req.parseAuthorization();

        tur.req.protocol = httpProtocol;

        Socket skt = ((SocketChannel)ChannelRudder.getChannel(sip.rudder)).socket();
        tur.req.remotePort = skt.getPort();

        tur.req.remoteAddress = skt.getInetAddress().getHostAddress();
        tur.req.serverAddress = skt.getLocalAddress().getHostAddress();
        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req.remoteAddress);

        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = sip.portDocker().secure();

        tur.go();
    }




}
