package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.croute.CrouteException;
import yokohama.baykit.croute.h3.H3Config;
import yokohama.baykit.croute.quic.Address;
import yokohama.baykit.croute.quic.Connection;
import yokohama.baykit.croute.quic.PacketHeader;
import yokohama.baykit.croute.quic.Quiche;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;


public final class QicTransporter implements Transporter {

    Multiplexer multiplexer;
    static HashMap<String, InboundShip> shipMap = new HashMap<>();
    H3Config h3Config = new H3Config();
    public int agentId;
    Rudder rudder;
    Port portDkr;
    boolean initialized;

    String serverName;
    byte[] serverNameBytes;

    // local bind address used when constructing quiche accept / recv
    Address localAddr;

    QicPacket tmpPostPacket;
    InetSocketAddress tmpPostAddress;

    @Override
    public String toString() {
        return "agt#" + agentId + " udp";
    }

    public void initUdp(
            int agentId,
            Rudder rd,
            Multiplexer mpx,
            Port dkr) {
        this.agentId = agentId;
        this.rudder = rd;
        this.multiplexer = mpx;
        this.portDkr = dkr;
        this.initialized = true;

        this.serverName = BayServer.getSoftwareName();
        this.serverNameBytes = serverName.getBytes();

        // Fixed local address (port value is not used by quiche for accept routing).
        this.localAddr = new Address("0.0.0.0", ((Port)dkr).port());
    }

    ////////////////////////////////////////////
    // Implements Transporter
    ////////////////////////////////////////////


    @Override
    public void init() {

    }

    @Override
    public NextSocketAction onConnect(Rudder rd) throws IOException {
        throw new Sink();
    }

    @Override
    public synchronized NextSocketAction onRead(Rudder rd, ByteBuffer buf, InetSocketAddress adr) throws IOException {

        byte[] packetBuf = new byte[buf.limit()];
        buf.get(packetBuf);

        BayLog.trace("%s notifyRead %d bytes", this, packetBuf.length);

        // parse first QUIC packet in byte data
        PacketHeader hdr;
        try {
            hdr = PacketHeader.parse(packetBuf, packetBuf.length, Address.CONN_ID_LEN);
        }
        catch(CrouteException e) {
            BayLog.error("%s Header parse error: %s(%d)", this, e.getMessage(), e.code);
            return NextSocketAction.Continue;
        }

        BayLog.debug("%s packet received :type=%d version=%d", this, hdr.type(), hdr.version());

        // find ship (by DCID)
        InboundShip sip = findShip(hdr.dcid());
        if (sip == null) {
            if (hdr.type() != QicType.Initial) {
                BayLog.warn("Client not registered (type=%d)", hdr.type());
            }
            else {
                sip = createShip(hdr, adr);
            }
        }

        if (sip != null) {
            sip.notifyRead(ByteBuffer.wrap(packetBuf));
        }

        // post packets
        postPackets();

        // cleanup closed connections
        cleanupConnections();

        return NextSocketAction.Continue;
    }

    @Override
    public void onError(Rudder rd, Throwable e) {
        BayLog.error(e);
    }

    @Override
    public void onClosed(Rudder rd) {

    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        multiplexer.reqConnect(rd, addr);
    }

    @Override
    public void reqRead(Rudder rd) {
        multiplexer.reqRead(rd);
    }

    @Override
    public boolean reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, boolean flush, DataConsumeListener listener) throws IOException {
        return multiplexer.reqWrite(rd, buf, adr, tag, flush, listener);
    }

    @Override
    public void reqTransfer(Rudder rd, Rudder fileRd, int ofs, int len, DataConsumeListener listener) throws IOException {
        throw new Sink();
    }

    @Override
    public void reqClose(Rudder rd) {
        multiplexer.reqClose(rd);
    }

    @Override
    public boolean checkTimeout(Rudder rd, int durationSec) {
        return false;
    }

    @Override
    public int getReadBufferSize() {
        return QicPacket.MAX_DATAGRAM_SIZE;
    }

    @Override
    public void printUsage(int indent) {

    }


    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    private InboundShip createShip(PacketHeader hdr, InetSocketAddress adr) throws IOException {

        if (!Quiche.versionIsSupported(hdr.version())) {
            negotiateVersion(hdr, adr);
            return null;
        }
        if(hdr.token().length == 0) {
            retry(hdr, adr);
            return null;
        }

        // Validate token
        final byte[] odcid = validateToken(adr.getAddress(), hdr.token());
        if (odcid == null) {
            throw new ProtocolException("Invalid address validation token");
        }

        // Use the DCID that was chosen in the retry response as our SCID.
        final byte[] srcConId = hdr.dcid();

        Address peer = Address.from(adr);
        Connection con = Connection.accept(
                srcConId,
                odcid,
                localAddr,
                peer,
                ((H3PortDocker)portDkr).config);

        BayLog.info("%s New connection scid=%s odcid=%s ref=%s", this, asHex(srcConId), asHex(odcid), con.getPtr());

        GrandAgent agt = GrandAgent.get(agentId);
        QicInboundHandler ibHandler = new QicInboundHandler();
        QicProtocolHandler hnd = new QicProtocolHandler(ibHandler, con, adr, localAddr, peer, h3Config, agt.netMultiplexer);
        ibHandler.init(hnd);
        InboundShip sip = new InboundShip();
        sip.initInbound(rudder, agentId, this, portDkr, hnd);
        hnd.setShip(sip);

        addShip(srcConId, sip);

        return sip;
    }

    synchronized InboundShip findShip(byte[] id) {
        return shipMap.get(asHex(id));
    }

    synchronized void addShip(byte[] id, InboundShip ship) {
        shipMap.put(asHex(id), ship);
    }


    /**
     * Generate a stateless retry token.
     *
     * The token includes the static string (server name) followed by the IP
     * address of the client and by the original destination connection ID generated
     * by the client.
     *
     * Note that this function is only an example and doesn't do any cryptographic
     * authenticate of the token. *It should not be used in production system*.
     */
    byte[] mintToken(PacketHeader hdr, InetAddress address) {
        final byte[] addr = address.getAddress();
        final byte[] dcid = hdr.dcid();
        final int total = serverNameBytes.length + addr.length + dcid.length;
        final ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(serverNameBytes);
        buf.put(addr);
        buf.put(dcid);
        return buf.array();
    }

    byte[] validateToken(InetAddress address, byte[] tkn) {
        if (tkn.length <= 8)
            return null;
        if (!Arrays.equals(serverNameBytes, Arrays.copyOfRange(tkn, 0, serverNameBytes.length)))
            return null;
        final byte[] addr = address.getAddress();
        if (!Arrays.equals(addr, Arrays.copyOfRange(tkn, serverNameBytes.length, addr.length + serverNameBytes.length)))
            return null;
        return Arrays.copyOfRange(tkn, serverNameBytes.length + addr.length, tkn.length);
    }

    /**
     * Negotiate Quic version
     */
    void negotiateVersion(PacketHeader hdr, InetSocketAddress adr) throws IOException {
        BayLog.info("%s Invalid quic version: %d. Start version negotiation", this, hdr.version());

        byte[] pktBytes;
        try {
            pktBytes = Quiche.negotiateVersion(hdr.scid(), hdr.dcid());
        }
        catch(CrouteException e) {
            throw new IOException("Quiche: cannot create negotiate version packet: " + e.getMessage(), e);
        }

        QicPacket pkt = new QicPacket();
        System.arraycopy(pktBytes, 0, pkt.buf, 0, pktBytes.length);
        pkt.bufLen = pktBytes.length;

        BayLog.info("%s start negotiation", this);
        tmpPostPacket = pkt;
        tmpPostAddress = adr;
    }

    /**
     * Retry
     */
    void retry(PacketHeader hdr, InetSocketAddress adr) throws IOException {
        byte[] newScid = Address.generateConnId();
        BayLog.info("%s Empty quic token. Retry scid=%s dcid=%s newid=%s",
                this, asHex(hdr.scid()), asHex(hdr.dcid()), asHex(newScid));

        byte[] token = mintToken(hdr, adr.getAddress());
        byte[] pktBytes;
        try {
            pktBytes = Quiche.retry(hdr.scid(), hdr.dcid(), newScid, token, hdr.version());
        }
        catch(CrouteException e) {
            throw new IOException("Quiche: cannot create retry packet: " + e.getMessage(), e);
        }

        QicPacket pkt = new QicPacket();
        System.arraycopy(pktBytes, 0, pkt.buf, 0, pktBytes.length);
        pkt.bufLen = pktBytes.length;
        tmpPostPacket = pkt;
        tmpPostAddress = adr;
    }



    /**
     * Send packets to client
     */
    boolean postPackets() throws IOException {
        boolean posted = false;

        // Check packets held in data listener
        if(tmpPostPacket != null) {
            multiplexer.reqWrite(rudder, tmpPostPacket.asBuffer(), tmpPostAddress, tmpPostPacket, true, null);
            tmpPostPacket = null;
            tmpPostAddress = null;
            posted = true;
        }

        // Check packets held in protocol handlers
        for (InboundShip s : shipMap.values()) {
            posted |= ((QicProtocolHandler)s.protocolHandler).postPackets();
        }

        return posted;
    }

    /**
     * Cleanup closed connections. Must iterate through Iterator.remove() since
     * HashMap#remove() during a keySet() for-each throws
     * ConcurrentModificationException.
     */
    void cleanupConnections() {
        for (java.util.Iterator<java.util.Map.Entry<String, InboundShip>> it = shipMap.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<String, InboundShip> entry = it.next();
            if (((QicProtocolHandler) entry.getValue().protocolHandler).isClosed()) {
                BayLog.debug("%s cleaning up conn=%s", this, entry.getKey());
                it.remove();
                BayLog.debug("%s # of clients: %d", this, shipMap.size());
            }
        }
    }


    @Override
    public void reset() {

    }

    static String asHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
