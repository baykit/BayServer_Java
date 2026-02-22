package yokohama.baykit.bayserver.docker.h3;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Connection;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;
import io.quiche4j.http3.Http3Connection;
import io.quiche4j.http3.Http3Header;
import io.quiche4j.http3.Http3EventListener;
import io.quiche4j.PacketHeader;
import io.quiche4j.PacketType;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;
import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.docker.Port;

public class Http3Server implements Port.SelfListener {

    private static final int MAX_DATAGRAM_SIZE = 1350;
    private static final String SERVER_NAME = "Quiche4j";
    private static final byte[] SERVER_NAME_BYTES = SERVER_NAME.getBytes();
    private static final int SERVER_NAME_BYTES_LEN = SERVER_NAME_BYTES.length;

    private static final String HEADER_NAME_STATUS = ":status";
    private static final String HEADER_NAME_SERVER = "server";
    private static final String HEADER_NAME_CONTENT_LENGTH = "content-length";

    private int agentId;
    private H3PortDocker portDocker;
    private DatagramSocket socket;

    public Http3Server(H3PortDocker portDkr, int agtId) {
        this.agentId = agtId;
        this.portDocker = portDkr;
    }


    ////////////////////////////////////////////
    // Implements SelfListener
    ////////////////////////////////////////////


    @Override
    public void listen() {
        new Thread(() -> {
            try {
                start();
                //Http3Server.main(new String[]{":2024"}, config);
            } catch (IOException e) {
                BayLog.fatal(e);
            }
        }).start();
    }

    @Override
    public void shutdown() {
        BayLog.debug("shutdown h3server");
        socket.close();
    }

    public void

    private void start() throws IOException {
        String hostname = portDocker.host() == null ? "" : portDocker.host();

        final byte[] buf = new byte[65535];
        final byte[] out = new byte[MAX_DATAGRAM_SIZE];

        socket = new DatagramSocket(portDocker.port(), InetAddress.getByName(hostname));
        socket.setSoTimeout(100);

        final Http3Config h3Config = new Http3ConfigBuilder().build();
        final byte[] connIdSeed = Quiche.newConnectionIdSeed();
        final HashMap<String, QicTicket> tickets = new HashMap<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        BayLog.trace(String.format("! listening on %s:%d", hostname, portDocker.port()));

        while (running.get()) {
            // READING
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    // TIMERS
                    for (QicTicket tkt : tickets.values()) {
                        tkt.conn.onTimeout();
                    }
                    break;
                }

                final int offset = packet.getOffset();
                final int len = packet.getLength();
                // xxx(okachaiev): can we avoid doing copy here?
                final byte[] packetBuf = Arrays.copyOfRange(packet.getData(), offset, len);

                BayLog.trace("> socket.recv " + len + " bytes");

                // PARSE QUIC HEADER
                final PacketHeader hdr;
                try {
                    int err[] = new int[1];
                    hdr = PacketHeader.parse(packetBuf, Quiche.MAX_CONN_ID_LEN, err);
                    BayLog.trace("> packet " + hdr);
                    if(hdr == null)
                        throw new Exception("Parse failed: " + err[0]);
                } catch (Exception e) {
                    BayLog.trace("! failed to parse headers " + e);
                    continue;
                }

                // SIGN CONN ID
                final byte[] connId = Quiche.signConnectionId(connIdSeed, hdr.destinationConnectionId());
                QicTicket tkt = tickets.get(Utils.asHex(hdr.destinationConnectionId()));
                if (null == tkt)
                    tkt = tickets.get(Utils.asHex(connId));
                if (null == tkt) {
                    // CREATE CLIENT IF MISSING
                    if (PacketType.INITIAL != hdr.packetType()) {
                        BayLog.trace("! wrong packet type");
                        continue;
                    }

                    // NEGOTIATE VERSION
                    if (!Quiche.versionIsSupported(hdr.version())) {
                        BayLog.trace("> version negotiation");

                        final int negLength = Quiche.negotiateVersion(hdr.sourceConnectionId(),
                                hdr.destinationConnectionId(), out);
                        if (negLength < 0) {
                            BayLog.trace("! failed to negotiate version " + negLength);
                            System.exit(1);
                            return;
                        }
                        final DatagramPacket negPacket = new DatagramPacket(out, negLength, packet.getAddress(),
                                packet.getPort());
                        socket.send(negPacket);
                        continue;
                    }

                    // RETRY IF TOKEN IS EMPTY
                    if (null == hdr.token()) {
                        BayLog.trace("> stateless retry");

                        final byte[] token = mintToken(hdr, packet.getAddress());
                        final int retryLength = Quiche.retry(hdr.sourceConnectionId(), hdr.destinationConnectionId(),
                                connId, token, hdr.version(), out);
                        if (retryLength < 0) {
                            BayLog.trace("! retry failed " + retryLength);
                            System.exit(1);
                            return;
                        }

                        BayLog.trace("> retry length " + retryLength);

                        final DatagramPacket retryPacket = new DatagramPacket(out, retryLength, packet.getAddress(),
                                packet.getPort());
                        socket.send(retryPacket);
                        continue;
                    }

                    // VALIDATE TOKEN
                    final byte[] odcid = validateToken(packet.getAddress(), hdr.token());
                    if (null == odcid) {
                        BayLog.trace("! invalid address validation token");
                        continue;
                    }

                    byte[] sourceConnId = connId;
                    final byte[] destinationConnId = hdr.destinationConnectionId();
                    if (sourceConnId.length != destinationConnId.length) {
                        BayLog.trace("! invalid destination connection id");
                        continue;
                    }
                    sourceConnId = destinationConnId;

                    final Connection conn = Quiche.accept(sourceConnId, odcid, new InetSocketAddress(packet.getAddress(), packet.getPort()), portDocker.config);

                    BayLog.trace("> new connection " + Utils.asHex(sourceConnId));

                    tkt = new QicTicket(conn, (InetSocketAddress)packet.getSocketAddress(), agentId, portDocker);
                    tickets.put(Utils.asHex(sourceConnId), tkt);

                    BayLog.trace("! # of clients: " + tickets.size());
                }

                // POTENTIALLY COALESCED PACKETS
                final Connection conn = tkt.conn;
                final int read = conn.recv(packetBuf, tkt.sender);
                if (read < 0 && read != Quiche.ErrorCode.DONE) {
                    BayLog.trace("> recv failed " + read);
                    break;
                }
                if (read <= 0)
                    break;

                BayLog.trace("> conn.recv " + read + " bytes");
                BayLog.trace("> conn.established " + conn.isEstablished());

                // ESTABLISH H3 CONNECTION IF NONE
                Http3Connection h3Conn = tkt.h3Conn;
                if ((conn.isInEarlyData() || conn.isEstablished()) && null == h3Conn) {
                    BayLog.trace("> handshake done " + conn.isEstablished());
                    h3Conn = Http3Connection.withTransport(conn, h3Config);
                    tkt.h3Conn =h3Conn;

                    BayLog.trace("> new H3 connection " + h3Conn);
                }

                if (null != h3Conn) {
                    // PROCESS WRITABLES
                    final QicTicket current = tkt;
                    for(long streamId: tkt.conn.writable()) {
                        tkt.handleWritable(streamId);
                    }

                    // H3 POLL
                    while (true) {
                        QicTicket tkt2 = tkt;
                        final long streamId = h3Conn.poll(new Http3EventListener() {
                            public void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody) {
                                tkt2.onHeaders(streamId, headers, hasBody);
                                //handleRequest(tkt2, streamId, headers);
                            }

                            public void onData(long streamId) {
                                tkt2.onData(streamId);
                            }

                            public void onFinished(long streamId) {
                                tkt2.onFinished(streamId);
                            }
                        });

                        if (streamId < 0 && streamId != Quiche.ErrorCode.DONE) {
                            BayLog.trace("! poll failed " + streamId);

                            // xxx(okachaiev): this should actially break from 2 loops
                            break;
                        }
                        // xxx(okachaiev): this should actially break from 2 loops
                        if (Quiche.ErrorCode.DONE == streamId)
                            break;

                        BayLog.trace("< poll " + streamId);
                    }
                }
            }

            // WRITES
            int len = 0;
            for (QicTicket tkt : tickets.values()) {
                final Connection conn = tkt.conn;

                while (true) {
                    len = conn.send(out);
                    if (len < 0 && len != Quiche.ErrorCode.DONE) {
                        BayLog.trace("! conn.send failed " + len);
                        break;
                    }
                    if (len <= 0)
                        break;
                    BayLog.trace("> conn.send " + len + " bytes");
                    final DatagramPacket packet = new DatagramPacket(out, len, tkt.sender);
                    socket.send(packet);
                }
            }

            // CLEANUP CLOSED CONNS
            Iterator<Map.Entry<String, QicTicket>> it = tickets.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, QicTicket> entry = it.next();
                if (entry.getValue().conn.isClosed()) {
                    BayLog.trace("> cleaning up " + entry.getKey());
                    it.remove();
                    BayLog.trace("! # of clients: " + tickets.size());
                }
            }

            // BACK TO READING
        }

        BayLog.trace("> server stopped");
        socket.close();
    }

    /**
     * Generate a stateless retry token.
     * 
     * The token includes the static string {@code "Quiche4j"} followed by the IP
     * address of the client and by the original destination connection ID generated
     * by the client.
     * 
     * Note that this function is only an example and doesn't do any cryptographic
     * authenticate of the token. *It should not be used in production system*.
     */
    public final static byte[] mintToken(PacketHeader hdr, InetAddress address) {
        final byte[] addr = address.getAddress();
        final byte[] dcid = hdr.destinationConnectionId();
        final int total = SERVER_NAME_BYTES_LEN + addr.length + dcid.length;
        final ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(SERVER_NAME_BYTES);
        buf.put(addr);
        buf.put(dcid);
        return buf.array();
    }

    public final static byte[] validateToken(InetAddress address, byte[] token) {
        if (token.length <= 8)
            return null;
        if (!Arrays.equals(SERVER_NAME_BYTES, Arrays.copyOfRange(token, 0, SERVER_NAME_BYTES_LEN)))
            return null;
        final byte[] addr = address.getAddress();
        if (!Arrays.equals(addr, Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN, addr.length + SERVER_NAME_BYTES_LEN)))
            return null;
        return Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN + addr.length, token.length);
    }
}