package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
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
import yokohama.baykit.bayserver.util.SimpleBuffer;

import java.io.IOException;
import java.util.ArrayList;

public class H2WarpHandler implements WarpHandler, H2Handler {

    public static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<H2Command, H2Packet> {

        @Override
        public ProtocolHandler<H2Command, H2Packet> createProtocolHandler(
                PacketStore<H2Packet> pktStore) {

            H2WarpHandler warpHandler = new H2WarpHandler();
            H2CommandUnPacker commandUnpacker = new H2CommandUnPacker(warpHandler);
            // serverMode=false on the warp side: we send the preface, we don't expect to receive it.
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
    // True once the H2 connection prelude (PRI preface + initial SETTINGS)
    // has been pushed onto the wire. Sent lazily on the first sendReqHeaders
    // call so we don't need a notifyConnect hook on this handler.
    boolean preludeSent = false;

    /**
     * Pending connection-level WINDOW_UPDATE bytes. Accumulated across DATA
     * frames and flushed when {@link #WINDOW_UPDATE_THRESHOLD} is reached.
     * The h2 spec requires the peer's connection window not to go negative,
     * not that we update on every frame. With initial conn window 65535 and
     * a 32 KiB threshold, the peer always retains at least 32 KiB of slack.
     */
    int pendingConnWindow = 0;
    /** Threshold for WINDOW_UPDATE coalescing. < initial window (65535). */
    static final int WINDOW_UPDATE_THRESHOLD = 32768;

    // Pooled HPACK encode scratch state (single-threaded per agent so no
    // synchronization). Per-request allocations of a 32 KiB SimpleBuffer +
    // builder + ArrayList showed up as the top JFR sample group on the
    // request-encode side after the dyn-table fix. The pool is reset at
    // the start of every sendReqHeaderCommand and the rendered bytes are
    // copied out into a tight byte[] before being attached to CmdHeaders.
    private final yokohama.baykit.bayserver.util.SimpleBuffer encodeBuf =
            new yokohama.baykit.bayserver.util.SimpleBuffer();
    private final HeaderBlockBuilder reqBlockBuilder = new HeaderBlockBuilder();
    private final HeaderBlockRenderer reqBlockRenderer = new HeaderBlockRenderer(encodeBuf);
    private final ArrayList<HeaderBlock> reqHeaderBlocks = new ArrayList<>();

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
        preludeSent = false;
        pendingConnWindow = 0;
    }

    /////////////////////////////////////
    // implements H2CommandHandler
    /////////////////////////////////////

    @Override
    public NextSocketAction handlePreface(CmdPreface cmd) throws IOException {
        // Client side never receives a preface: only servers do.
        throw new IllegalStateException();
    }

    @Override
    public NextSocketAction handleData(CmdData cmd) throws IOException {
        Tour tur = ship().getTour(cmd.streamId);
        boolean available = tur.res.sendResContent(Tour.TOUR_ID_NOCHECK, cmd.data, cmd.start, cmd.length);

        // Replenish flow-control windows so the upstream backend can keep
        // sending. The h2 spec only requires that the peer's window not go
        // negative, not that we update on every frame. Coalesce per-stream
        // and per-connection increments to threshold to cut WindowUpdate
        // posts ~4x on multi-chunk responses (1 MB / 16 KiB frame * 2
        // updates per frame = ~128 posts -> ~32 posts). Threshold (32 KiB)
        // is well below the 65535-byte initial window, so the peer always
        // retains plenty of slack.
        if (cmd.length > 0) {
            // Per-stream: skip if this is the END_STREAM frame (stream is
            // closing; nginx returns STREAM_CLOSED for updates on a closed
            // stream). Otherwise accumulate and flush on threshold.
            if (!cmd.flags.endStream()) {
                WarpData wd = WarpData.get(tur);
                wd.pendingStreamWindow += cmd.length;
                if (wd.pendingStreamWindow >= WINDOW_UPDATE_THRESHOLD) {
                    CmdWindowUpdate upd = new CmdWindowUpdate(cmd.streamId);
                    upd.windowSizeIncrement = wd.pendingStreamWindow;
                    ship().post(upd);
                    wd.pendingStreamWindow = 0;
                }
            }
            // Per-connection: always accumulate, flush on threshold.
            pendingConnWindow += cmd.length;
            if (pendingConnWindow >= WINDOW_UPDATE_THRESHOLD) {
                CmdWindowUpdate upd2 = new CmdWindowUpdate(0);
                upd2.windowSizeIncrement = pendingConnWindow;
                ship().post(upd2);
                pendingConnWindow = 0;
            }
        }

        // End-of-stream cleanup must run even when the inbound write buffer
        // is full (= !available). Front-end-closed tours never see their
        // inbound write listener fire (no consumer is reading), so a Suspend
        // here would freeze the END_STREAM forever -> tur.res.endResContent
        // is never called -> returnTour never fires -> per-agent
        // TourStore.activeTourMap drifts up to MAX_TOURS -> 503 storm.
        // endResContent is async (its CmdEndContent post flows through the
        // SpiderMultiplexer.reqWrite null-state short-circuit when the
        // rudder is gone), so it is safe to fire here regardless of
        // back-pressure state.
        if (cmd.flags.endStream()) {
            endResContent(tur);
        }

        if(!available)
            return NextSocketAction.Suspend;

        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleHeaders(CmdHeaders cmd) throws IOException {
        Tour tur = ship().getTour(cmd.streamId);
        if (tur == null) {
            BayLog.error("%s no tour for streamId=%d", ship(), cmd.streamId);
            return NextSocketAction.Continue;
        }
        WarpData wtur = WarpData.get(tur);

        if (tur.res.headerSent())
            throw new ProtocolException("Header command not expected");

        ArrayList<HeaderBlock> headerBlocks;
        try {
            headerBlocks = new HeaderBlockParser(cmd.data, cmd.start, cmd.length).parseHeaderBlocks();
        } catch (RuntimeException e) {
            throw new ProtocolException("HPACK decode failed: " + e.getMessage());
        }

        for (HeaderBlock blk : headerBlocks) {
            if (blk.op == HeaderBlock.HeaderOp.UpdateDynamicTableSize) {
                resHeaderTbl.setSize(blk.size);
                continue;
            }
            analyzer.analyzeHeaderBlock(blk, resHeaderTbl);
            if (analyzer.name == null)
                continue;

            if (analyzer.name.charAt(0) != ':') {
                tur.res.headers.add(analyzer.name, analyzer.value);
            }
            else if (HeaderTable.PSEUDO_HEADER_STATUS.equals(analyzer.name)) {
                try {
                    tur.res.headers.setStatus(Integer.parseInt(analyzer.value));
                } catch (NumberFormatException e) {
                    BayLog.error(e);
                }
            }
            // other pseudo-headers in a response are protocol errors per RFC,
            // but we ignore them here since the warp peer is trusted.
        }

        if (cmd.flags.endHeaders()) {
            tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);

            // Wire up the back-pressure resume hook (mirrors H1WarpHandler):
            //  * the consumer listener fires when the downstream write buffer
            //    drains; on `resume==true` we ask the warp ship to read more
            //    from the backend
            //  * without this, sendResContent's internal consumed() callback
            //    finds resConsumeListener=null and tears the agent down with
            //    "Consume listener is null" — exactly what we hit before this
            //    fix on h2c proxy benches
            if (!cmd.flags.endStream()) {
                WarpShip wsip = ship();
                int sid = wsip.id();
                tur.res.setConsumeListener((len, resume) -> {
                    if (resume) {
                        wsip.resumeRead(sid);
                    }
                });
            }

            if (cmd.flags.endStream()) {
                endResContent(tur);
            }
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handlePriority(CmdPriority cmd) throws IOException {
        // PRIORITY frames are deprecated in RFC 9113; we don't act on them.
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleSettings(CmdSettings cmd) throws IOException {
        if(!cmd.flags.ack()){
            CmdSettings res = new CmdSettings(0, new H2Flags(H2Flags.FLAGS_ACK));
            protocolHandler.post(res, true);
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleWindowUpdate(CmdWindowUpdate cmd) throws IOException {
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleGoAway(CmdGoAway cmd) throws IOException {
        // GOAWAY (RFC 9113 §6.8). Common case is errorCode == NO_ERROR
        // (0) when the peer (e.g. nginx) hits its per-connection
        // request budget and wants to rotate connections.
        //
        // Strategy: exclude this ship from the multiplex pool so no
        // further tours attach to it, fail the in-flight tours, and
        // close. Trying to "drain" the in-flight tours rarely works
        // in practice: peers send GOAWAY immediately followed by FIN,
        // and tours waiting for response data deadlock until the
        // socket-read timeout fires. Failing them fast and letting
        // the next attempt route to a fresh ship is much cheaper
        // than absorbing a multi-second timeout per stuck tour.
        if (cmd.errorCode == 0) {
            BayLog.debug("%s received GoAway (NO_ERROR, lastStreamId=%d)",
                    ship(), cmd.lastStreamId);
        } else {
            BayLog.error(ship() + " received GoAway: code=" + cmd.errorCode + " desc="
                    + H2ErrorCode.msg.getMessage(Integer.toString(cmd.errorCode))
                    + " debug=" + new String(cmd.debugData));
        }
        ship().docker().excludeFromPool(ship());
        ship().notifyServiceUnavailable("Received GoAway packet");
        return NextSocketAction.Close;
    }

    @Override
    public NextSocketAction handlePing(CmdPing cmd) throws IOException {
        CmdPing res = new CmdPing(cmd.streamId, new H2Flags(H2Flags.FLAGS_ACK), cmd.opaqueData);
        protocolHandler.post(res, true);
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

    @Override
    public NextSocketAction handleContinuation(CmdContinuation cmd) throws IOException {
        // We do not currently split inbound HEADERS across CONTINUATION frames
        // (h2c backends are expected to send headers in a single HEADERS frame
        // for the body sizes used in this bench). If a backend chooses to use
        // CONTINUATION the response will be malformed; treat it as a no-op for
        // now. A proper fix would buffer fragments until END_HEADERS.
        return NextSocketAction.Continue;
    }

    /////////////////////////////////////
    // implements WarpHandler
    /////////////////////////////////////
    @Override
    public int nextWarpId() {
        // Client-initiated H2 streams use odd ids: 1, 3, 5, ...
        int cur = curStreamId;
        curStreamId += 2;
        return cur;
    }

    @Override
    public int maxMultiplexedTours() {
        // H2 supports stream multiplexing on a shared TCP connection. The
        // upper bound here gates how many tours can ride a single WarpShip
        // before the warp pool opens another backend connection. 100 is a
        // conservative starting point; tuning surface (HtpWarpDocker
        // parameter) can be added later.
        return 100;
    }

    @Override
    public WarpData newWarpData(int warpId) {
        return new WarpData(ship(), warpId);
    }

    @Override
    public void sendReqHeaders(Tour tur) throws IOException {
        sendPreludeIfNeeded();
        sendReqHeaderCommand(tur);
    }

    @Override
    public void sendReqContent(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendReqDataCommand(tur, buf, start, len, lis);
    }

    @Override
    public void sendEndReq(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {
        // If the request had no body, sendReqHeaders already set END_STREAM
        // on the final HEADERS/CONTINUATION frame. Sending another empty
        // DATA frame with END_STREAM here would be a frame on an already-
        // half-closed stream and backends respond with RST_STREAM
        // (STREAM_CLOSED, code 5). In that case just notify the consumer
        // listener so the deferred-write callback is satisfied.
        boolean reqHadBody =
                tur.req.headers.contains("content-length")
                        || tur.req.headers.contains("transfer-encoding");
        if (!reqHadBody) {
            if (lis != null) lis.dataConsumed(true);
            return;
        }
        int streamId = WarpData.get(tur).warpId;
        CmdData cmd = new CmdData(streamId, null, new byte[0], 0, 0);
        cmd.flags.setEndStream(true);
        // WarpShip.post handles the !connected case via cmdBuf; once the
        // connection is up, post becomes a flush-true forward to
        // protocolHandler.post under the hood.
        ship().post(cmd, lis);
    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
        // No-op: H2WarpDocker forces H2 from the start, so there's no
        // ALPN-driven protocol switch to verify.
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
        return (WarpShip) protocolHandler.ship;
    }

    /**
     * Send the H2 connection prelude (RFC 7540 § 3.5):
     *   1. The 24-byte client connection preface "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
     *   2. An initial SETTINGS frame
     * This is called lazily on the first sendReqHeaders so it runs after the
     * TCP connect completes (= after WarpShip.notifyConnect started draining
     * the queued tours) but before the first request HEADERS go out.
     */
    void sendPreludeIfNeeded() throws IOException {
        if (preludeSent) return;

        // 1. Connection preface — empty CmdPreface. Its pack() emits the 24
        //    preface bytes raw (not in H2 frame format).
        // Use ship().post (not protocolHandler.post): WarpShip.post buffers
        // commands in cmdBuf while !connected and drains them via flush()
        // after notifyConnect. protocolHandler.post bypasses that and tries
        // to write straight to a connection-pending channel (NotYetConnected).
        CmdPreface preface = new CmdPreface(0, null);
        ship().post(preface);

        // 2. Initial SETTINGS frame on the control stream (id=0), no ACK.
        // INITIAL_WINDOW_SIZE here only applies to per-stream windows on
        // newly-created streams; the connection-level window starts at the
        // RFC 7540 default of 65535 and can only be bumped via WINDOW_UPDATE
        // on stream 0 (see step 3 below).
        // We use a generous 16 MiB so that single large responses (1 MB
        // body) don't even hit the per-stream window threshold; backends
        // (Nginx, Envoy, …) honour any value <= 2^31-1.
        CmdSettings set = new CmdSettings(H2ProtocolHandler.CTL_STREAM_ID);
        set.streamId = 0;
        set.items.add(new CmdSettings.Item(CmdSettings.MAX_CONCURRENT_STREAMS,
                Math.max(BayServer.harbor.maxToursPerShip(), 100)));
        set.items.add(new CmdSettings.Item(CmdSettings.INITIAL_WINDOW_SIZE,
                INITIAL_WINDOW_SIZE_OUT));
        ship().post(set);

        // 3. Connection-level WINDOW_UPDATE: bump stream 0's window from
        //    the spec-mandated 65535 to (initial + INITIAL_WINDOW_SIZE_OUT)
        //    so multi-MB bodies aren't rate-limited by the connection
        //    window before our reactive WINDOW_UPDATEs in handleData kick in.
        CmdWindowUpdate connUp = new CmdWindowUpdate(0);
        connUp.windowSizeIncrement = INITIAL_WINDOW_SIZE_OUT;
        ship().post(connUp);

        preludeSent = true;
    }

    // 16 MiB advertised stream + connection window. Far above any single
    // bench body; reactive WINDOW_UPDATEs in handleData top it back up.
    private static final int INITIAL_WINDOW_SIZE_OUT = 16 * 1024 * 1024;

    void sendReqHeaderCommand(Tour tur) throws IOException {
        Town town = tur.town;

        String townPath = town.name();
        if (!townPath.endsWith("/"))
            townPath += "/";
        WarpShip sip = ship();
        String newUri = sip.docker().warpBase() + tur.req.uri.substring(townPath.length());

        // Reuse the pooled encode buffer + block list. Both are
        // single-threaded per agent.
        encodeBuf.reset();
        reqHeaderBlocks.clear();
        HeaderBlockBuilder bld = reqBlockBuilder;
        ArrayList<HeaderBlock> headerBlocks = reqHeaderBlocks;

        headerBlocks.add(bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_METHOD, tur.req.method, reqHeaderTbl));
        headerBlocks.add(bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_PATH, newUri, reqHeaderTbl));
        headerBlocks.add(bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_SCHEME,
                tur.isSecure ? "https" : "http", reqHeaderTbl));
        headerBlocks.add(bld.buildHeaderBlock(HeaderTable.PSEUDO_HEADER_AUTHORITY,
                sip.docker().host() + ":" + sip.docker().port(), reqHeaderTbl));

        // Regular request headers: must be lowercase, must not include
        // connection-specific fields (RFC 7540 § 8.1.2.2). The Host header
        // is already covered by :authority above and must not be duplicated
        // here (some backends accept both, but a single source of truth is
        // safer).
        for (String name : tur.req.headers.headerNames()) {
            String lower = name.toLowerCase();
            if (lower.equals("connection") || lower.equals("host")
                    || lower.equals("keep-alive") || lower.equals("transfer-encoding")
                    || lower.equals("upgrade") || lower.equals("proxy-connection")) {
                continue;
            }
            for (String value : tur.req.headers.headerValues(name)) {
                headerBlocks.add(bld.buildHeaderBlock(lower, value, reqHeaderTbl));
            }
        }

        reqBlockRenderer.renderHeaderBlocks(headerBlocks);
        // Snapshot the rendered bytes into a tight, owned array. The
        // WarpShip cmdBuf path can hold the resulting CmdHeaders past
        // this method's return -- if we left the data pointing at the
        // pooled SimpleBuffer, the next sendReqHeaderCommand would
        // overwrite it before the buffered command reaches pack().
        byte[] encoded = java.util.Arrays.copyOf(encodeBuf.bytes(), encodeBuf.length());

        int streamId = WarpData.get(tur).warpId;
        boolean endStream = !tur.req.headers.contains("content-length")
                && !tur.req.headers.contains("transfer-encoding");

        int pos = 0;
        int len = encoded.length;
        // Always emit at least one HEADERS frame, even if HPACK rendered to
        // zero bytes (defensive; in practice we always have :method etc.).
        if (len == 0) {
            CmdHeaders hcmd = new CmdHeaders(streamId);
            hcmd.excluded = false;
            hcmd.data = encoded;
            hcmd.start = 0;
            hcmd.length = 0;
            hcmd.flags.setEndHeaders(true);
            if (endStream) hcmd.flags.setEndStream(true);
            sip.post(hcmd);
            return;
        }

        while (len > 0) {
            int chunkLen = Math.min(len, H2Packet.DEFAULT_PAYLOAD_MAXLEN);

            H2Command cmd;
            if (pos == 0) {
                CmdHeaders hcmd = new CmdHeaders(streamId);
                hcmd.excluded = false;
                hcmd.data = encoded;
                hcmd.start = pos;
                hcmd.length = chunkLen;
                cmd = hcmd;
            }
            else {
                CmdContinuation ccmd = new CmdContinuation(streamId);
                ccmd.data = encoded;
                ccmd.start = pos;
                ccmd.length = chunkLen;
                cmd = ccmd;
            }

            cmd.flags.setPadded(false);
            pos += chunkLen;
            len -= chunkLen;
            if (len == 0) {
                cmd.flags.setEndHeaders(true);
                // Mark end-of-stream on the final HEADERS/CONTINUATION when
                // there's no request body; otherwise sendEndReq will close it.
                if (endStream) cmd.flags.setEndStream(true);
            }
            // Use WarpShip.post: it buffers when !connected (which is the
            // case during startWarpTour, before notifyConnect fires).
            sip.post(cmd);
        }
    }


    void sendReqDataCommand(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        int streamId = WarpData.get(tur).warpId;
        CmdData cmd =
                new CmdData(
                        streamId,
                        null,
                        buf,
                        start,
                        len);
        ship().post(cmd, lis);
    }


    private void endResContent(Tour tur) throws IOException {
        // Order matters: endWarpTour reads WarpData.get(tur) (= the
        // tour's req contentHandler). tur.res.endResContent triggers
        // tour.reset() which clears that contentHandler, so we must
        // close out the warp side first. Mirrors H1WarpHandler.endResContent.
        ship().endWarpTour(tur, true);
        tur.res.endResContent(Tour.TOUR_ID_NOCHECK);
    }
}
