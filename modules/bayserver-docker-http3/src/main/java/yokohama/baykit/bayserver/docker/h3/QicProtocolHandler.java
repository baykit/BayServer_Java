package yokohama.baykit.bayserver.docker.h3;

import io.quiche4j.Connection;
import io.quiche4j.Quiche;
import io.quiche4j.http3.*;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class QicProtocolHandler
        extends ProtocolHandler<QicCommand, QicCommandPacket, QicCommandType>
        implements Http3EventListener {

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
        List<Http3Header> headers;
        byte[] body;
        boolean fin;
        long written;
        DataConsumeListener listener;
        boolean finished;

        PartialResponse(List<Http3Header> headers) {
            this(headers, null, 0, false, null);
        }

        PartialResponse(byte[] body, int ofs, DataConsumeListener lis) {
            this(null, body, ofs, false, lis);
        }

        PartialResponse(boolean fin, DataConsumeListener lis) {
            this(null, new byte[0], 0, fin, lis);
        }

        private PartialResponse(List<Http3Header> headers, byte[] body, int ofs, boolean fin, DataConsumeListener lis) {
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
    final HashMap<Long, ArrayList<PartialResponse>> partialResponses = new HashMap<>();
    final Http3Config h3Config;
    final Multiplexer multiplexer;
    InboundShip ship;
    Http3Connection h3con;

    public static final String PROTOCOL = "HTTP/3";

    public QicProtocolHandler(QicInboundHandler handler, Connection con, InetSocketAddress adr, Http3Config cfg, Multiplexer mpx) {
        super(null, null, null, null, handler, true);
        this.con = con;
        this.sender = adr;
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
        int n = con.recv(bytes, sender);

        if (n < bytes.length)
            BayLog.info("Packet Read failed ? %d/%d", n, bytes.length);

        if (n == Quiche.ErrorCode.DONE) {
            BayLog.debug("No data");
        }
        else if (n < 0) {
            throw new ProtocolException("Invalid packet: recvLen=" + QuicheErrorCode.getMessage(n));
        }
        else {
            // ESTABLISH H3 CONNECTION IF NONE
            Http3Connection h3Con = http3Connection();

            if (h3Con != null) {
                processH3Connection(h3Con);
            }
        }

        return NextSocketAction.Continue;
    }

    ////////////////////////////////////////////
    // Implements Http3EventListener
    ////////////////////////////////////////////

    @Override
    public void onHeaders(long stmId, List<Http3Header> req_headers, boolean hasBody) {
        ((QicCommandHandler)commandHandler).handleHeaders(new CmdHeader(stmId, req_headers, hasBody));
    }

    @Override
    public void onData(long stmId) {
        ((QicCommandHandler)commandHandler).handleData(new CmdData(stmId));
    }

    @Override
    public void onFinished(long stmId) {
        ((QicCommandHandler)commandHandler).handleFinished(new CmdFinished(stmId));
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

    public Http3Connection http3Connection() {
        if (h3con == null) {
            if ((con.isInEarlyData() || con.isEstablished())) {
                BayLog.debug("%s Handshake done con=%d", this, con.getPointer());

                h3con = Http3Connection.withTransport(con, h3Config);

                BayLog.debug("%s New H3 connection: %s", this, h3con);
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
        //BayLog.debug("stm#%d added: len=%d", stmId, parts.size());
    }

    /**
     * Process commands in HTTP3 onnection
     * @param h3con
     */
    void processH3Connection(Http3Connection h3con) throws IOException {

        BayLog.trace("%s processH3Connection", this);
        //flushWritable();

        // Polling h3 data
        while (true) {
            BayLog.trace("%s poll: %s", this, Thread.currentThread());
            long stmId = h3con.poll(this);
            //BayLog.info("%s stm#%d polled %s", this, streamId, Thread.currentThread());

            if (stmId == Quiche.ErrorCode.DONE) {
                BayLog.trace("%s No polling data: %s", this, Thread.currentThread());
                break;
            }

            if (stmId < 0) {
                BayLog.error("%s poll failed stm=%d", this, stmId);
                break;
            }
        }

        flushWritable();
    }

    void flushWritable() throws IOException {
        //BayLog.debug("%s flush writable", this);
        for(long stmId: con.writable()) {
            //BayLog.debug("%s stm#%d writable", this, stmId);
            onStreamWritable(stmId);
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
                int cap = con.streamCapacity(stmId);

                BayLog.trace("stm#%d writable capacity=%d", stmId, cap);
                if (cap < 0) {
                    // Error
                    if (cap == Quiche.ErrorCode.STREAM_STOPPED) {
                        BayLog.debug("stm#%d writable, but stream stopped", stmId);
                        break;
                    }
                    else {
                        throw h3Error("writable, but stream stopped", (int) stmId, cap);
                    }
                }
                else if (cap == 0) {
                    BayLog.debug("stm#%d writable, but no capacity", stmId);
                    break;
                } else {
                    BayLog.trace("%s handleWritable stm#%d part cap=%d", this, stmId, cap);

                    if (part.headers != null) {
                        // send header
                        long n = h3con.sendResponse(stmId, part.headers, part.fin);

                        if (n < 0) {
                            // Error
                            short h3err = Http3.ErrorCode.h3Error((short) n);
                            short qerr = Http3.ErrorCode.quicheError((short) n);

                            if (h3err == Http3.ErrorCode.TRANSPORT_ERROR || n == Http3.ErrorCode.STREAM_BLOCKED) {
                                if( qerr == Quiche.ErrorCode.DONE) {
                                    BayLog.debug("%s stm#%d retry to send header: DONE returned (retry)", this, stmId);
                                    break;
                                }
                                throw quicheError("retry to send header failed", (int)stmId, qerr);
                            }
                            else {
                                throw h3Error("h3: send body failed: ", (int)stmId, h3err);
                            }
                        }

                        BayLog.debug("%s stm#%d h3: retry to send header succeed", this, stmId);
                        part.finished = true;

                    }
                    else {
                        // send body
                        byte[] body = Arrays.copyOfRange(part.body, (int) part.written, part.body.length);
                        long n = h3con.sendBody(stmId, body, part.fin);

                        int tryBytes = (int) (part.body.length - part.written);
                        BayLog.trace("%s stm#%d retry to send body %d bytes: try=%d written=%d/%d fin=%s", this, stmId, n, tryBytes, part.written, part.body.length, part.fin);

                        if (n < 0) {
                            // Error
                            if (n == Http3.ErrorCode.DONE) {
                                BayLog.debug("%s stm#%d retry to send body: DONE returned (retry)", this, stmId);
                                break;
                            }
                            else {
                                BayLog.error("%s stm#%d h3: retry to send body failed :%s(%d)", this, stmId, H3ErrorCode.getMessage((int) n), n);
                                break;
                            }
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
            listeners.forEach(lis -> lis.dataConsumed());


        if(parts.isEmpty()) {
            partialResponses.remove(stmId);
        }

        postPackets();
    }

    boolean postPackets() throws IOException {
        boolean posted = false;
        while (true) {
            InetSocketAddress addr[] = new InetSocketAddress[1];
            QicPacket pkt = new QicPacket();

            int len = con.send(pkt.buf, addr);
            if(len == Quiche.ErrorCode.DONE) {
                //BayLog.debug("DONE");
                break;
            }

            if (len < 0) {
                throw new IOException("Quiche: cannot send packet:" + QuicheErrorCode.getMessage(len));
            }

            //BayLog.debug("%s post packet len=%d addr=%s", this, len, addr[0]);
            pkt.bufLen = len;
            multiplexer.reqWrite(ship.rudder, pkt.asBuffer(), addr[0], pkt, null);
            posted = true;
        }
        return posted;
    }

    boolean isClosed() {
        return con.isClosed();
    }

}
