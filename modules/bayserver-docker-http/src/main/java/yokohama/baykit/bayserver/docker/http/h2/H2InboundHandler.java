package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.InboundHandler;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourReq;
import yokohama.baykit.bayserver.tour.TourStore;
import yokohama.baykit.bayserver.util.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;

public class H2InboundHandler implements H2Handler, InboundHandler {

    public static class InboundProtocolHandlerFactory implements ProtocolHandlerFactory<H2Command, H2Packet> {

        @Override
        public ProtocolHandler<H2Command, H2Packet> createProtocolHandler(
                PacketStore<H2Packet> pktStore) {

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
    int windowSize = BayServer.harbor.shipBufferSize();
    final H2Settings settings = new H2Settings();

    // RFC 7540 § 6.9.1: the flow-control window must not exceed 2^31-1.
    // We track (but do not yet enforce on send) the outbound window so that
    // WINDOW_UPDATE frames that would overflow it can be rejected per spec.
    // Actually respecting the window while emitting DATA frames would require
    // deeper changes to the send path; that is left for a later pass and is
    // what the remaining h2spec failures exercise.
    private static final long MAX_WINDOW = 0x7FFFFFFFL;
    private static final long DEFAULT_INITIAL_WINDOW = 65535L;
    long connSendWindow = DEFAULT_INITIAL_WINDOW;
    final java.util.Map<Integer, Long> streamSendWindows = new java.util.HashMap<>();
    final HeaderBlockAnalyzer analyzer = new HeaderBlockAnalyzer();
    public final HeaderTable reqHeaderTbl = HeaderTable.createDynamicTable();
    public final HeaderTable resHeaderTbl = HeaderTable.createDynamicTable();
    SimpleBuffer headerBuffer = new SimpleBuffer();



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
        headerBuffer.reset();

        // Flow-control tracking is per-connection; pooled handlers must start
        // each new connection with fresh windows.
        connSendWindow = DEFAULT_INITIAL_WINDOW;
        streamSendWindows.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements InboundHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        HeaderBlockBuilder bld = new HeaderBlockBuilder();

        ArrayList<HeaderBlock> headerBlocks = new ArrayList<>();

        HeaderBlock blk = bld.buildHeaderBlock(":status", Integer.toString(tur.res.headers.status()), resHeaderTbl);
        headerBlocks.add(blk);

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
                    headerBlocks.add(blk);
                }
            }
        }

        SimpleBuffer buf = new SimpleBuffer();
        new HeaderBlockRenderer(buf).renderHeaderBlocks(headerBlocks);

        int pos = 0;
        int len = buf.length();
        while(len > 0) {
            int chunkLen = Math.min(len, H2Packet.DEFAULT_PAYLOAD_MAXLEN);

            H2Command cmd;
            if(pos == 0) {
                CmdHeaders hcmd = new CmdHeaders(tur.req.key);
                hcmd.excluded = false;
                hcmd.data = buf.bytes();
                hcmd.start = pos;
                hcmd.length = len;
                cmd = hcmd;
            }
            else {
                CmdContinuation ccmd = new CmdContinuation(tur.req.key);
                ccmd.data = buf.bytes();
                ccmd.start = pos;
                ccmd.length = len;
                cmd = ccmd;
            }

            cmd.flags.setPadded(false);

            pos += chunkLen;
            len -= chunkLen;
            if(len == 0) {
                cmd.flags.setEndHeaders(true);
            }

            protocolHandler.post(cmd, false);
        }

    }

    @Override
    public boolean sendContent(Tour tur, byte[] bytes, int ofs, final int len, DataConsumeListener lis) throws IOException {
        CmdData cmd = new CmdData(tur.req.key, null, bytes, ofs, len);
        return protocolHandler.post(cmd, false, lis);
    }

    @Override
    public void transferContent(Tour tur, Rudder fileRd, int ofs, int len, DataConsumeListener lis) {
        throw new Sink();
    }

    @Override
    public void sendEndTour(Tour tur, DataConsumeListener lis) throws IOException {
        CmdData cmd = new CmdData(tur.req.key, null, new byte[0], 0, 0);
        cmd.flags.setEndStream(true);
        protocolHandler.post(cmd, true, lis);
        // NOTE: We intentionally do NOT mark the stream CLOSED in the
        // command unpacker here. Doing so while the last DATA frames are
        // still in flight under concurrent streams caused the send pipeline
        // to deadlock for 100KB+ H2 responses (see bench commit notes).
        // The tour's completion listener takes care of freeing BayServer's
        // own per-tour resources; the H2 state map then stays as
        // HALF_CLOSED_REMOTE until the connection closes, which means we
        // may over-count slightly against MAX_CONCURRENT_STREAMS on very
        // long-lived connections — that cost is acceptable compared to the
        // hang. A proper fix belongs with the outbound flow-control rework.
    }

    @Override
    public boolean onProtocolError(ProtocolException e) {
        BayLog.debug(e);
        BayLog.error(e, e.getMessage());
        CmdGoAway cmd = new CmdGoAway(H2ProtocolHandler.CTL_STREAM_ID);
        cmd.streamId = 0;
        cmd.lastStreamId = 0;
        // H2ProtocolException carries a caller-specified error code (e.g.
        // FLOW_CONTROL_ERROR, COMPRESSION_ERROR); bare ProtocolException
        // defaults to PROTOCOL_ERROR as per RFC 7540 § 5.4.
        cmd.errorCode = (e instanceof H2ProtocolException)
                ? ((H2ProtocolException) e).errorCode
                : H2ErrorCode.PROTOCOL_ERROR;
        cmd.debugData = "Thank you!".getBytes(StandardCharsets.UTF_8);
        try {
            // Defer the close until the GOAWAY frame has actually been written.
            // Calling postClose() synchronously would often close the socket
            // before the GOAWAY reaches the peer (h2spec would see
            // "unexpected EOF" instead of the GOAWAY frame).
            protocolHandler.post(cmd, true, avail -> {
                protocolHandler.ship.postClose();
            });
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
        set.items.add(new CmdSettings.Item(CmdSettings.MAX_CONCURRENT_STREAMS, BayServer.harbor.maxToursPerShip()));
        set.items.add(new CmdSettings.Item(CmdSettings.INITIAL_WINDOW_SIZE, windowSize));
        protocolHandler.post(set, true);

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
            return NextSocketAction.Continue;
        }

        if(cmd.flags.endHeaders()) {
            return onEndHeader(tur, cmd.data, cmd.start, cmd.length);
        }
        else {
            this.headerBuffer.put(cmd.data, cmd.start, cmd.length);
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

        // RFC 7540 § 8.1.2.6: if content-length is given, the sum of DATA
        // payload lengths MUST match it. Detect the END_STREAM boundary so a
        // mismatch is flagged before the handler processes the payload.
        if (cmd.flags.endStream()) {
            int contLen = tur.req.headers.contentLength();
            if (contLen >= 0 && tur.req.bytesPosted + cmd.length != contLen)
                throw new ProtocolException(
                        "content-length " + contLen + " does not match DATA payload "
                                + (tur.req.bytesPosted + cmd.length));
        }

        try {
            boolean success = true;
            if(cmd.length > 0) {
                int tid = tur.tourId;
                success =
                        tur.req.postReqContent(
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
                                        try {
                                            protocolHandler.post(upd, false);
                                            protocolHandler.post(upd2, true);
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
                        BayLog.debug("%s Delay send error", tur);
                        throw tur.error;
                    }
                    else {
                        endReqContent(tur.id(), tur);
                    }
                }
            }

            if(!success)
                return NextSocketAction.Suspend;
            else
                return NextSocketAction.Continue;
        } catch (HttpException e) {
            tur.req.abort();
            tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);
            return NextSocketAction.Continue;
        }
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
                    // RFC 7540 § 6.5.2: ENABLE_PUSH must be 0 or 1.
                    if (item.value != 0 && item.value != 1)
                        throw new ProtocolException(
                                "SETTINGS_ENABLE_PUSH must be 0 or 1, got " + item.value);
                    settings.enablePush = (item.value != 0);
                    break;
                case CmdSettings.MAX_CONCURRENT_STREAMS:
                    settings.maxConcurrentStreams = item.value;
                    break;
                case CmdSettings.INITIAL_WINDOW_SIZE:
                    // RFC 7540 § 6.5.2: INITIAL_WINDOW_SIZE must not exceed
                    // 2^31-1; larger values are FLOW_CONTROL_ERROR. The field
                    // is parsed as a signed int so a negative value also means
                    // it overflowed the 31-bit limit.
                    if (item.value < 0)
                        throw new H2ProtocolException(H2ErrorCode.FLOW_CONTROL_ERROR,
                                "SETTINGS_INITIAL_WINDOW_SIZE exceeds 2^31-1: " + item.value);
                    settings.initialWindowSize = item.value;
                    break;
                case CmdSettings.MAX_FRAME_SIZE:
                    // RFC 7540 § 6.5.2: MAX_FRAME_SIZE must be within
                    // [2^14, 2^24-1] (16384..16777215).
                    if (item.value < H2Packet.DEFAULT_PAYLOAD_MAXLEN
                            || item.value > H2Packet.MAX_PAYLOAD_MAXLEN)
                        throw new ProtocolException(
                                "SETTINGS_MAX_FRAME_SIZE out of range: " + item.value);
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
        protocolHandler.post(res, true);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleWindowUpdate(CmdWindowUpdate cmd) throws IOException {
        // RFC 7540 § 6.9: a WINDOW_UPDATE with increment 0 is PROTOCOL_ERROR
        // (or FLOW_CONTROL_ERROR at the stream level — both terminate the peer).
        if(cmd.windowSizeIncrement == 0)
            throw new ProtocolException("Invalid increment value");
        BayLog.debug("%s handleWindowUpdate: stmid=%d siz=%d", ship(),  cmd.streamId, cmd.windowSizeIncrement);

        // RFC 7540 § 6.9.1: adding the increment must not push the window
        // above 2^31-1. Overflow at the connection level is a connection
        // error FLOW_CONTROL_ERROR (GOAWAY); at the stream level it is a
        // stream error (RST_STREAM) so the connection itself survives.
        if (cmd.streamId == 0) {
            connSendWindow += (cmd.windowSizeIncrement & 0xFFFFFFFFL);
            if (connSendWindow > MAX_WINDOW)
                throw new H2ProtocolException(H2ErrorCode.FLOW_CONTROL_ERROR,
                        "Connection send window overflow: " + connSendWindow);
        } else {
            long win = streamSendWindows.getOrDefault(cmd.streamId, DEFAULT_INITIAL_WINDOW)
                    + (cmd.windowSizeIncrement & 0xFFFFFFFFL);
            if (win > MAX_WINDOW) {
                CmdRstStream rst = new CmdRstStream(cmd.streamId);
                rst.errorCode = H2ErrorCode.FLOW_CONTROL_ERROR;
                protocolHandler.post(rst, true);
                streamSendWindows.remove(cmd.streamId);
                return NextSocketAction.Continue;
            }
            streamSendWindows.put(cmd.streamId, win);
        }
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
        BayLog.debug("%s handle_ping: stm=%d ack=%b", sip, cmd.streamId, cmd.flags.ack());

        // RFC 7540 § 6.7: a PING frame with the ACK flag is a response to a
        // PING the endpoint sent; we never send PINGs, and in any case an
        // endpoint MUST NOT respond to PING with ACK set.
        if (cmd.flags.ack())
            return NextSocketAction.Continue;

        CmdPing res = new CmdPing(cmd.streamId, new H2Flags(H2Flags.FLAGS_ACK), cmd.opaqueData);
        protocolHandler.post(res, true);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleRstStream(CmdRstStream cmd) throws IOException {
        BayLog.debug("%s received RstStream: stmid=%d code=%d desc=%s",
                ship(), cmd.streamId, cmd.errorCode, H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode)));
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleContinuation(CmdContinuation cmd) throws IOException {

        Tour tur = getTour(cmd.streamId);
        if(tur == null) {
            throw new IllegalArgumentException("Invalid stream id: " + cmd.streamId);
        }

        this.headerBuffer.put(cmd.data, cmd.start, cmd.length);
        if(cmd.flags.endHeaders()) {
            return onEndHeader(tur, headerBuffer.bytes(), 0, headerBuffer.length());

        }
        return NextSocketAction.Continue;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static boolean hasUpperCase(String s) {
        if (s == null)
            return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z')
                return true;
        }
        return false;
    }

    InboundShip ship() {
        return (InboundShip) protocolHandler.ship;
    }

    Tour getTour(int key) {
        return ship().getTour(key);
    }

    private void endReqContent(int checkId, Tour tur) throws IOException, HttpException {
        tur.req.endReqContent(checkId);
    }

    void startTour(Tour tur) throws HttpException {
        InboundShip sip = ship();

        tur.req.parseHostPort(sip.portDocker().secure() ? 443 : 80);
        tur.req.parseAuthorization();

        tur.req.protocol = httpProtocol;

        // Get remote address
        String clientAdr = tur.req.headers.get(Headers.X_FORWARDED_FOR);
        if (clientAdr != null) {
            tur.req.remoteAddress = clientAdr;
            tur.req.remotePort = -1;
        }
        else {
            try {
                NetworkChannelRudder nrd = (NetworkChannelRudder) ship().rudder;
                tur.req.remotePort = nrd.getRemotePort();
                tur.req.remoteAddress = nrd.getRemoteAddress().getHostAddress();
                tur.req.serverAddress = nrd.getLocalAddress().getHostAddress();
            }
            catch(IOException e) {
                // Unix domain socket
                tur.req.remotePort = -1;
                tur.req.remoteAddress = null;
                tur.req.serverAddress = null;
            }
        }

        tur.req.remoteHostFunc = new TourReq.DefaultRemoteHostResolver(tur.req.remoteAddress);

        tur.req.serverPort = tur.req.reqPort;
        tur.req.serverName = tur.req.reqHost;
        tur.isSecure = sip.portDocker().secure();

        tur.go();
    }


    NextSocketAction onEndHeader(Tour tur, byte[] buf, int start, int len) throws IOException {

        ArrayList<HeaderBlock> headerBlocks;
        try {
            headerBlocks = new HeaderBlockParser(buf, start, len).parseHeaderBlocks();
        } catch (RuntimeException e) {
            // Truncated/corrupt HPACK input can surface as ArrayIndexOutOfBoundsException
            // (short frame) or IllegalArgumentException (other decode errors). Convert
            // those to a protocol-level COMPRESSION_ERROR per RFC 7541 § 2.3.3 so the
            // connection is closed with a proper GOAWAY instead of a fatal agent crash.
            throw new ProtocolException("HPACK decode failed: " + e.getMessage());
        }

        // Pseudo-header + header-field validation (RFC 7540 § 8.1.2). We track
        // state across the whole header block so duplicates and ordering can
        // be detected.
        boolean sawMethod = false, sawScheme = false, sawPath = false, sawAuthority = false;
        boolean sawRegularHeader = false;

        for(HeaderBlock blk : headerBlocks) {
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

            // § 8.1.2: header field names must be lowercase.
            if (hasUpperCase(analyzer.rawName))
                throw new ProtocolException(
                        "Header name must be lowercase: " + analyzer.rawName);

            if (analyzer.pseudo) {
                // § 8.1.2.1: pseudo-header fields must precede regular headers.
                if (sawRegularHeader)
                    throw new ProtocolException(
                            "Pseudo-header " + analyzer.rawName + " appears after a regular header");

                // § 8.1.2.1: request pseudo-headers are :method, :scheme,
                // :path, :authority. :status is only for responses.
                switch (analyzer.rawName) {
                    case HeaderTable.PSEUDO_HEADER_METHOD:
                        if (sawMethod)
                            throw new ProtocolException("Duplicated :method");
                        sawMethod = true;
                        tur.req.method = analyzer.method;
                        break;
                    case HeaderTable.PSEUDO_HEADER_SCHEME:
                        if (sawScheme)
                            throw new ProtocolException("Duplicated :scheme");
                        sawScheme = true;
                        break;
                    case HeaderTable.PSEUDO_HEADER_PATH:
                        if (sawPath)
                            throw new ProtocolException("Duplicated :path");
                        // § 8.1.2.3: :path must not be empty for http/https.
                        if (analyzer.path == null || analyzer.path.isEmpty())
                            throw new ProtocolException("Empty :path pseudo-header");
                        sawPath = true;
                        tur.req.uri = analyzer.path;
                        break;
                    case HeaderTable.PSEUDO_HEADER_AUTHORITY:
                        if (sawAuthority)
                            throw new ProtocolException("Duplicated :authority");
                        sawAuthority = true;
                        tur.req.headers.add(analyzer.name, analyzer.value);
                        break;
                    case HeaderTable.PSEUDO_HEADER_STATUS:
                        throw new ProtocolException(
                                ":status pseudo-header is invalid in a request");
                    default:
                        throw new ProtocolException(
                                "Unknown pseudo-header: " + analyzer.rawName);
                }
            }
            else {
                sawRegularHeader = true;
                // § 8.1.2.2: connection-specific header fields are forbidden
                // in HTTP/2. The only allowed TE value is "trailers".
                if (analyzer.name.equalsIgnoreCase("connection")
                        || analyzer.name.equalsIgnoreCase("keep-alive")
                        || analyzer.name.equalsIgnoreCase("proxy-connection")
                        || analyzer.name.equalsIgnoreCase("transfer-encoding")
                        || analyzer.name.equalsIgnoreCase("upgrade"))
                    throw new ProtocolException(
                            "Connection-specific header in HTTP/2: " + analyzer.name);
                if (analyzer.name.equalsIgnoreCase("te")
                        && !"trailers".equalsIgnoreCase(analyzer.value))
                    throw new ProtocolException(
                            "TE header with value other than 'trailers': " + analyzer.value);
                tur.req.headers.add(analyzer.name, analyzer.value);
            }
        }

        // § 8.1.2.3: request MUST include :method, :scheme, :path.
        if (!sawMethod)
            throw new ProtocolException("Missing :method pseudo-header");
        if (!sawScheme)
            throw new ProtocolException("Missing :scheme pseudo-header");
        if (!sawPath)
            throw new ProtocolException("Missing :path pseudo-header");

        tur.req.protocol = "HTTP/2.0";
        BayLog.debug("%s H2 read header method=%s protocol=%s uri=%s contlen=%d",
                ship(), tur.req.method, tur.req.protocol, tur.req.uri, tur.req.headers.contentLength());

        HttpUtil.checkUri(tur.req.uri);
        int reqContLen = tur.req.headers.contentLength();

        if(reqContLen > 0) {
            tur.req.setLimit(reqContLen);
        }

        try {
            if(tur.req.uri == null) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "Missing uri");
            }

            startTour(tur);
            if (tur.req.headers.contentLength() <= 0) {
                endReqContent(tur.id(), tur);
            }
        } catch (HttpException e) {
            BayLog.debug("%s Http error occurred: %s", this, e);
            if(reqContLen <= 0) {
                // no post data
                tur.req.abort();
                tur.res.sendHttpException(Tour.TOUR_ID_NOCHECK, e);

                return NextSocketAction.Continue;
            }
            else {
                // Delay send
                tur.error = e;
                tur.req.setReqContentHandler(ReqContentHandler.devNull);
                return NextSocketAction.Continue;
            }
        }

        return NextSocketAction.Continue;
    }
}
