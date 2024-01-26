package yokohama.baykit.bayserver.docker.h3;

import io.quiche4j.*;
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;
import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;


public final class QicDataListener implements DataListener {

    Multiplexer multiplexer;
    byte[] connIdSeed = Quiche.newConnectionIdSeed();
    static HashMap<String, InboundShip> shipMap = new HashMap<>();
    Http3Config h3Config = new Http3ConfigBuilder().build();
    public int agentId;
    Rudder rudder;
    Port portDkr;
    boolean initialized;

    String serverName;
    byte[] serverNameBytes;

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

    }

    ////////////////////////////////////////////
    // Implements WaterCraft
    ////////////////////////////////////////////

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public synchronized NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {

        byte[] packetBuf = new byte[buf.limit()];
        buf.get(packetBuf);

        BayLog.trace("%s notifyRead %d bytes", this, packetBuf.length);

        // parse first QUIC packet in byte data
        int err[] = new int[1];
        PacketHeader hdr = PacketHeader.parse(packetBuf, Quiche.MAX_CONN_ID_LEN, err);
        if(hdr == null) {
            BayLog.error("%s Header parse error: %s(%d)", this, H3ErrorCode.getMessage(err[0]), err[0]);
            return NextSocketAction.Continue;
        }

        BayLog.debug("%s packet received :%s", this, hdr);

        // Sign connection id
        byte[] conId = Quiche.signConnectionId(connIdSeed, hdr.destinationConnectionId());

        // find ship
        InboundShip sip = getShip(conId, hdr);
        if (sip == null) {
            //BayLog.debug("%s handler not found", this);
            if (hdr.packetType() != PacketType.INITIAL) {
                BayLog.warn("Client not registered");
            }
            else {
                sip = createShip(conId, hdr, adr);
            }
        }

        if (sip != null) {
            sip.notifyRead(ByteBuffer.wrap(packetBuf));
        }

        // post packets
        boolean posted = postPackets();

        // cleanup closed connections
        cleanupConnections();

        if(posted)
            return NextSocketAction.Write;
        else
            return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyEof() {
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
        throw new Sink();
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        BayLog.error(e);
        return false;
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.error(e);
    }

    @Override
    public void notifyClose() {

    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return false;
    }


    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////


    /**
     * Get client object held in this instance
     */
    InboundShip getShip(byte[] conId, PacketHeader hdr) throws IOException {

        InboundShip sip = findShip(hdr.destinationConnectionId());
        if (sip == null)
            sip = findShip(conId);
        //BayLog.info("%s search client conid=%s client=%s", this, Utils.asHex(conId), client);
        return sip;
    }


    InboundShip createShip(byte[] conId, PacketHeader hdr, InetSocketAddress adr) throws IOException {

        if (!Quiche.versionIsSupported(hdr.version())) {
            negotiateVersion(hdr, adr);
            return null;
        }
        if(hdr.token() == null) {
            retry(conId, hdr, adr);
            return null;
        }

        // Validate token
        final byte[] odcid = validateToken(adr.getAddress(), hdr.token());
        if (odcid == null) {
            throw new ProtocolException("Invalid address validation token");
        }

        byte[] srcConId = conId;
        final byte[] dstConId = hdr.destinationConnectionId();
        if (srcConId.length != dstConId.length) {
            throw new ProtocolException("Invalid destination connection id");
        }
        srcConId = dstConId;

        Connection con =
                Quiche.accept(
                        srcConId,
                        odcid,
                        adr,
                        ((H3PortDocker)portDkr).config);

        BayLog.info("%s New connection scid=%s odcid=%s ref=%d", this, Utils.asHex(srcConId), Utils.asHex(odcid), con.getPointer());

        GrandAgent agt = GrandAgent.get(agentId);
        QicProtocolHandler hnd = new QicProtocolHandler(con, adr, h3Config, agt.netMultiplexer);
        InboundShip sip = new InboundShip();
        sip.initInbound(rudder, agentId, agt.netMultiplexer, portDkr, hnd);
        hnd.setShip(sip);

        addShip(srcConId, sip);

        return sip;
    }

    synchronized InboundShip findShip(byte[] id) {
        //BayLog.info("%s getClient: id=%s", this, Utils.asHex(id));
        return shipMap.get(Utils.asHex(id));
    }

    synchronized void addShip(byte[] id, InboundShip ship) {
        //BayLog.info("%s addClient: id=%s, cln=%s", this, Utils.asHex(id), cln);
        shipMap.put(Utils.asHex(id), ship);
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
    byte[] mintToken(PacketHeader hdr, InetAddress address) {
        final byte[] addr = address.getAddress();
        final byte[] dcid = hdr.destinationConnectionId();
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
     * @param hdr
     * @param adr
     * @throws IOException
     */
    void negotiateVersion(PacketHeader hdr, InetSocketAddress adr) throws IOException {
        BayLog.info("%s Invalid quic version: %d. Start version negotiation", this, hdr.version());

        QicPacket pkt = new QicPacket();
        int len =
                Quiche.negotiateVersion(
                        hdr.sourceConnectionId(),
                        hdr.destinationConnectionId(),
                        pkt.buf);
        if (len < 0) {
            throw new IOException("Quiche: cannot create negotiate version packet:" + len);
        }

        BayLog.info("%s start negotiation", this);
        pkt.bufLen = len;
        tmpPostPacket = pkt;
        tmpPostAddress = adr;
    }

    /**
     * Retry
     */
    void retry(byte[] conId, PacketHeader hdr, InetSocketAddress adr) throws IOException {
        BayLog.info("%s Empty quic token. Retry scid=%s dcid=%s newid=%s",
                this, Utils.asHex(hdr.sourceConnectionId()), Utils.asHex(hdr.destinationConnectionId()), Utils.asHex(conId));

        byte[] token = mintToken(hdr, adr.getAddress());
        QicPacket pkt = new QicPacket();
        int len =
                Quiche.retry(
                        hdr.sourceConnectionId(),
                        hdr.destinationConnectionId(),
                        conId,
                        token,
                        hdr.version(),
                        pkt.buf);
        if (len < 0) {
            throw new IOException("Quiche: cannot create retry packet:" + QuicheErrorCode.getMessage(len));
        }

        pkt.bufLen = len;
        tmpPostPacket = pkt;
        tmpPostAddress = adr;
    }



    /**
     * Send packets to client
     * @return
     * @throws IOException
     */
    boolean postPackets() throws IOException {
        boolean posted = false;

        // Check packets held in data listener
        if(tmpPostPacket != null) {
            multiplexer.reqWrite(rudder, tmpPostPacket.asBuffer(), tmpPostAddress, tmpPostPacket, null);
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
     * Cleanup closed connections
     */
    void cleanupConnections() {
        for (String connId : shipMap.keySet()) {

            if (((QicProtocolHandler)shipMap.get(connId).protocolHandler).isClosed()) {
                System.out.println("> cleaning up " + connId);

                shipMap.remove(connId);

                System.out.println("! # of clients: " + shipMap.size());
            }
        }
    }


}
