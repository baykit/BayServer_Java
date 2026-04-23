package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.h3.command.CmdData;
import yokohama.baykit.bayserver.docker.h3.command.CmdFinished;
import yokohama.baykit.bayserver.docker.h3.command.CmdHeader;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import yokohama.baykit.bayserver.rudder.DatagramChannelRudder;
import yokohama.baykit.croute.CrouteException;
import yokohama.baykit.croute.binding.Binding;
import yokohama.baykit.croute.binding.QuicheStructs;
import yokohama.baykit.croute.h3.Event;
import yokohama.baykit.croute.h3.H3Config;
import yokohama.baykit.croute.h3.H3Connection;
import yokohama.baykit.croute.h3.Headers;
import yokohama.baykit.croute.quic.Address;
import yokohama.baykit.croute.quic.Connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;

public class QicProtocolHandler
        extends ProtocolHandler<QicCommand, QicCommandPacket> {

    enum ReqState {
        ReadHeader,
        ReadContent,
        End,
    };

    interface DelayedProcess {
        void process() throws Exception;
    }

    class QicTourExtra implements Reusable {

        ReqState reqState = ReqState.ReadHeader;
        ArrayList<DelayedProcess> delayedProcesses = new ArrayList<>();

        @Override
        public void reset() {
            delayedProcesses.clear();
            reqState = ReqState.ReadHeader;
        }
    }

    private static final int MAX_BUFFER_SIZE = 16384;

    static class PartialResponse {
        List<Headers.Header> headers;
        byte[] body;
        boolean fin;
        long written;
        DataConsumeListener listener;
        boolean finished;

        PartialResponse(List<Headers.Header> headers) {
            this(headers, null, 0, false, null);
        }

        PartialResponse(byte[] body, int ofs, DataConsumeListener lis) {
            this(null, body, ofs, false, lis);
        }

        PartialResponse(boolean fin, DataConsumeListener lis) {
            this(null, new byte[0], 0, fin, lis);
        }

        private PartialResponse(List<Headers.Header> headers, byte[] body, int ofs, boolean fin, DataConsumeListener lis) {
            this.headers = headers;
            if(body != null)
                this.body = Arrays.copyOfRange(body, ofs, body.length);
            this.listener = lis;
            this.fin = fin;
            this.written = 0;
        }
    }

    final Connection con;
    final InetSocketAddress sender;
    final Address localAddress;
    final Address peerAddress;
    final QuicheStructs.RecvInfo recvInfo;
    final HashMap<Long, ArrayList<PartialResponse>> partialResponses = new HashMap<>();
    final H3Config h3Config;
    final Multiplexer multiplexer;

    // Reusable scratch state for the outgoing packet loop. Every post reuses
    // the same buffer so quiche_conn_send does not allocate per datagram.
    private final byte[] sendScratch = new byte[QicPacket.MAX_DATAGRAM_SIZE];
    private final ByteBuffer sendBuffer = ByteBuffer.wrap(sendScratch);
    private final QuicheStructs.SendInfo sendInfo = new QuicheStructs.SendInfo();

    InboundShip ship;
    H3Connection h3con;

    public static final String PROTOCOL = "HTTP/3";

    public QicProtocolHandler(QicInboundHandler handler, Connection con, InetSocketAddress adr,
                              Address localAddress, Address peerAddress,
                              H3Config cfg, Multiplexer mpx) {
        super(null, null, null, null, handler, true);
        this.con = con;
        this.sender = adr;
        this.localAddress = localAddress;
        this.peerAddress = peerAddress;
        // quiche_recv_info is a pair of sockaddr pointers (local, peer). croute
        // dropped the builder helper, so we populate the Structure manually.
        this.recvInfo = new QuicheStructs.RecvInfo();
        this.recvInfo.from   = peerAddress.toSockaddrPtr();
        this.recvInfo.fromLen = peerAddress.toSockaddrLen();
        this.recvInfo.to      = localAddress.toSockaddrPtr();
        this.recvInfo.toLen   = localAddress.toSockaddrLen();
        this.recvInfo.write();
        this.h3Config = cfg;
        this.multiplexer = mpx;
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    public void setShip(InboundShip ship) {
        this.ship = ship;
    }

    ////////////////////////////////////////////
    // Implements ProtocolHandler
    ////////////////////////////////////////////

    @Override
    public String protocol() {
        return "h3";
    }

    @Override
    public int maxReqPacketDataSize() {
        return MAX_BUFFER_SIZE;
    }

    @Override
    public int maxResPacketDataSize() {
        return MAX_BUFFER_SIZE;
    }

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        byte[] bytes = buf.array();
        long n;
        try {
            n = con.recv(bytes, recvInfo);
        }
        catch(CrouteException e) {
            // A recv() error means quiche rejected the packet (invalid transport
            // param, protocol violation, etc). quiche has moved the connection
            // into the closing state and queued a CONNECTION_CLOSE frame. Let
            // the caller flush outgoing packets so the client gets the error
            // response before we tear down. Do not propagate as an exception
            // or postPackets() will be skipped.
            BayLog.debug("%s recv rejected: %s (code=%d)", this, e.getMessage(), e.code);
            return NextSocketAction.Continue;
        }

        if (n < bytes.length)
            BayLog.info("Packet Read failed ? %d/%d", n, bytes.length);

        if (n == CrouteException.ERR_DONE) {
            BayLog.debug("No data");
        }
        else {
            // ESTABLISH H3 CONNECTION IF NONE
            H3Connection h3Con = http3Connection();

            if (h3Con != null) {
                processH3Connection(h3Con);
            }
        }

        return NextSocketAction.Continue;
    }

    ////////////////////////////////////////////
    // H3 event dispatch
    ////////////////////////////////////////////

    void dispatchEvent(Event ev) {
        long stmId = ev.getStreamId();
        switch (ev.getType()) {
            case HEADERS -> ((QicCommandHandler)commandHandler).handleHeaders(
                    new CmdHeader(stmId, ev.getHeaders(), false));
            case DATA -> ((QicCommandHandler)commandHandler).handleData(new CmdData(stmId));
            case FINISHED -> ((QicCommandHandler)commandHandler).handleFinished(new CmdFinished(stmId));
            default -> BayLog.debug("%s stm#%d ignored event: %s", this, stmId, ev.getType());
        }
    }

    ////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////

    IOException quicheError(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + ": " + QuicheErrorCode.getMessage(code) + "(" + code + ")");
    }

    IOException h3Error(String msg, long stmId, int code) {
        return new IOException("stm#" + stmId + " " + msg + H3ErrorCode.getMessage(code) + "(" + code + ")");
    }

    /**
     * When H3 returns H3_ERR_TRANSPORT_ERROR, the underlying QUIC error is
     * available on the connection via quiche_conn_peer_error /
     * quiche_conn_local_error. We do not need the full payload here, just a
     * hint to include in the exception message. Returning -1 means no error
     * was recorded (or the introspection was not useful).
     */
    long peerOrLocalErrorCode() {
        com.sun.jna.ptr.ByteByReference isApp = new com.sun.jna.ptr.ByteByReference();
        LongByReference errorCode = new LongByReference();
        com.sun.jna.ptr.PointerByReference reason = new com.sun.jna.ptr.PointerByReference();
        com.sun.jna.ptr.NativeLongByReference reasonLen = new com.sun.jna.ptr.NativeLongByReference();
        if (Binding.lib().quiche_conn_peer_error(con.getPtr(), isApp, errorCode, reason, reasonLen)) {
            return errorCode.getValue();
        }
        if (Binding.lib().quiche_conn_local_error(con.getPtr(), isApp, errorCode, reason, reasonLen)) {
            return errorCode.getValue();
        }
        return -1;
    }

    public H3Connection http3Connection() {
        if (h3con == null) {
            if ((con.isEarlyData() || con.isEstablished())) {
                BayLog.debug("%s Handshake done con=%s", this, con.getPtr());

                h3con = new H3Connection(con, h3Config);

                BayLog.debug("%s New H3 connection", this);
            }
        }
        return h3con;
    }

    void addPartialResponse(long stmId, PartialResponse part) {
        ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if(parts == null) {
            parts = new ArrayList<>();
            partialResponses.put(stmId, parts);
        }
        parts.add(part);
    }

    /**
     * Process commands in HTTP3 connection
     */
    void processH3Connection(H3Connection h3con) throws IOException {

        BayLog.trace("%s processH3Connection", this);

        // Polling h3 events (croute's pollAll was removed; loop over poll()).
        try {
            Event ev;
            while ((ev = h3con.poll()) != null) {
                dispatchEvent(ev);
            }
        }
        catch(CrouteException e) {
            BayLog.debug("%s h3 poll failed: %s (code=%d)", this, e.getMessage(), e.code);
        }

        flushWritable();
    }

    void flushWritable() throws IOException {
        // quiche_conn_writable returns a stream-id iterator that the caller
        // must free. croute dropped the List<Long> materialising helper.
        Pointer iter = Binding.lib().quiche_conn_writable(con.getPtr());
        if (iter == null) return;
        try {
            LongByReference idRef = new LongByReference();
            while (Binding.lib().quiche_stream_iter_next(iter, idRef)) {
                onStreamWritable(idRef.getValue());
            }
        } finally {
            Binding.lib().quiche_stream_iter_free(iter);
        }
    }

    void onStreamWritable(long stmId) throws IOException {
        ArrayList<PartialResponse> parts = partialResponses.get(stmId);
        if (parts == null)
            return;

        BayLog.debug("%s stm#%d writable qlen=%d", this, stmId, parts.size());
        ArrayList<DataConsumeListener> listeners = null;
        try {
            for (PartialResponse part : parts) {
                long cap;
                try {
                    cap = con.streamCapacity(stmId);
                }
                catch(CrouteException e) {
                    if (e.code == CrouteException.ERR_STREAM_STOPPED) {
                        BayLog.debug("stm#%d writable, but stream stopped", stmId);
                        break;
                    }
                    throw h3Error("writable, but stream stopped", (int) stmId, e.code);
                }

                BayLog.trace("stm#%d writable capacity=%d", stmId, cap);
                if (cap == 0) {
                    BayLog.debug("stm#%d writable, but no capacity", stmId);
                    break;
                }

                BayLog.trace("%s handleWritable stm#%d part cap=%d", this, stmId, cap);

                if (part.headers != null) {
                    // send header (croute now wants FfiHeaders for the 1:1 wrapper)
                    Headers.FfiHeaders ffi = Headers.toFfi(part.headers);
                    try {
                        h3con.sendHeaders(stmId, ffi, part.fin);
                    }
                    catch(CrouteException e) {
                        if (e.code == CrouteException.H3_ERR_STREAM_BLOCKED) {
                            BayLog.debug("%s stm#%d retry to send header: blocked", this, stmId);
                            break;
                        }
                        else if (e.code == CrouteException.H3_ERR_TRANSPORT_ERROR) {
                            long qerr = peerOrLocalErrorCode();
                            throw new IOException("stm#" + stmId + " h3: send header failed (transport): qerr=" + qerr);
                        }
                        else {
                            throw h3Error("h3: send header failed: ", (int)stmId, e.code);
                        }
                    }

                    BayLog.debug("%s stm#%d h3: retry to send header succeed", this, stmId);
                    part.finished = true;

                }
                else {
                    // send body
                    byte[] body = Arrays.copyOfRange(part.body, (int) part.written, part.body.length);
                    long n;
                    try {
                        n = h3con.sendBody(stmId, body, part.fin);
                    }
                    catch(CrouteException e) {
                        BayLog.error("%s stm#%d h3: retry to send body failed :%s(%d)", this, stmId, H3ErrorCode.getMessage(e.code), e.code);
                        break;
                    }

                    int tryBytes = (int) (part.body.length - part.written);
                    BayLog.trace("%s stm#%d retry to send body %d bytes: try=%d written=%d/%d fin=%s", this, stmId, n, tryBytes, part.written, part.body.length, part.fin);

                    if (n == 0) {
                        // DONE -> retry later
                        BayLog.debug("%s stm#%d retry to send body: DONE returned (retry)", this, stmId);
                        break;
                    }
                    else if (tryBytes > 0 && n == 0) {
                        BayLog.error("%s stm#%d h3: no data written", this, stmId);
                    }
                    else {
                        part.written += n;
                        if (part.written == part.body.length)
                            part.finished = true;
                        else
                            break;
                    }
                }
            }

            for (Iterator<PartialResponse> it = parts.iterator(); it.hasNext(); ) {
                PartialResponse part = it.next();
                if (!part.finished)
                    break;

                if (part.listener != null) {
                    if(listeners == null)
                        listeners = new ArrayList<>();
                    // notification is delayed to avoid deadlock
                    listeners.add(part.listener);
                }
                it.remove();
            }
        }
        catch(IOException e){
            BayLog.error(e);
            parts.clear();
        }


        if(listeners != null)
            listeners.forEach(lis -> lis.dataConsumed(true));


        if(parts.isEmpty()) {
            partialResponses.remove(stmId);
        }

        postPackets();
    }

    boolean postPackets() throws IOException {
        boolean posted = false;
        while (true) {
            if (con.isClosed()) {
                // Already fully drained; nothing more to send.
                break;
            }
            long n;
            try {
                n = con.send(sendScratch, sendInfo);
            }
            catch(CrouteException e) {
                // Race: connection transitioned to closed between the check
                // above and send(). Treat as drained.
                BayLog.debug("%s send on closing connection: %s", this, e.getMessage());
                break;
            }
            if (n == CrouteException.ERR_DONE) {
                break;
            }

            // Hand the packet off to the multiplexer, which handles back
            // pressure if the socket send buffer is full. The per-packet
            // QicPacket copy is required because the multiplexer retains the
            // buffer until the write completes; sendScratch has to stay
            // reusable for the next iteration.
            QicPacket pkt = new QicPacket();
            System.arraycopy(sendScratch, 0, pkt.buf, 0, (int) n);
            pkt.bufLen = (int) n;
            multiplexer.reqWrite(ship.rudder, pkt.asBuffer(), sender, pkt, true, null);
            posted = true;
        }
        return posted;
    }

    boolean isClosed() {
        return con.isClosed();
    }

}
