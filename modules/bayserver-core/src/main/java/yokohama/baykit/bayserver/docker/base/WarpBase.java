package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.RudderStateStore;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.common.WarpShipStore;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Warp;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.AsynchronousSocketChannelRudder;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class WarpBase extends ClubBase implements Warp {

    /**
     * jdk.net.ExtendedSocketOptions.TCP_QUICKACK if running on a JDK that
     * exposes it (= JDK 10+ on Linux). Cached once at class init via
     * reflection so we don't take a hard compile-time dep on the
     * jdk.net module. null on platforms / JDKs that don't support it.
     *
     * Why: when the upstream backend (= php-fpm and friends) doesn't set
     * TCP_NODELAY on its accepted socket, response bodies between MSS
     * and a few MTUs get split into two segments, the second carrying
     * the small tail. The tail waits for an ACK (= Nagle algorithm on
     * the backend), the proxy schedules the ACK with the kernel's
     * delayed-ACK timer (~40ms on Linux), and the request hits a 40ms
     * stall per round-trip. Setting TCP_QUICKACK on the warp side tells
     * the kernel to send the ACK immediately, breaking the loop.
     */
    private static final java.net.SocketOption<Boolean> TCP_QUICKACK_OPT;
    static {
        java.net.SocketOption<Boolean> opt = null;
        try {
            Class<?> cls = Class.forName("jdk.net.ExtendedSocketOptions");
            Object o = cls.getField("TCP_QUICKACK").get(null);
            if (o instanceof java.net.SocketOption) {
                @SuppressWarnings("unchecked")
                java.net.SocketOption<Boolean> so = (java.net.SocketOption<Boolean>) o;
                opt = so;
            }
        }
        catch (Throwable ignore) {
            // Pre-JDK-10 / non-Linux: leave null and skip.
        }
        TCP_QUICKACK_OPT = opt;
    }

    private static void applyQuickAck(java.nio.channels.NetworkChannel ch) {
        if (TCP_QUICKACK_OPT == null) return;
        try { ch.setOption(TCP_QUICKACK_OPT, true); }
        catch (Throwable t) {
            BayLog.debug(t, "TCP_QUICKACK setsockopt failed (skipping)");
        }
    }

    class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            stores.put(agentId, new WarpShipStore(maxShips));
        }

        @Override
        public void remove(int agentId) {
            stores.remove(agentId);
        }
    }

    public String scheme;
    public String host;
    public int port = -1;
    public String warpBase;
    protected int maxShips = -1;
    SocketAddress hostAddr;
    int timeoutSec = -1; // -1 means "Use harbor.socketTimeoutSec"

    final List<Tour> tourList = new ArrayList<>();

    /** Agent ID => WarpShipStore */
    final Map<Integer, WarpShipStore> stores = new HashMap<>();

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract boolean secure();
    protected abstract String protocol();
    protected abstract Transporter newTransporter(GrandAgent agent, NetworkChannelRudder rd, Ship sip) throws IOException;

    /////////////////////////////////////
    // Implements Docker
    /////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(StringUtil.empty(warpBase))
            warpBase = "/";

        try {
            if(StringUtil.isSet(host) && host.startsWith(":unix:")) {
                String sktPath = host.substring(6);
                hostAddr = SysUtil.getUnixDomainSocketAddress(sktPath);
                port = -1;
            }
            else {
                if(port <= 0)
                    port = 80;
                hostAddr = new InetSocketAddress(InetAddress.getByName(host), port);
            }
        }
        catch (IOException e) {
            throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.CFG_INVALID_WARP_DESTINATION(host), e);
        }

        GrandAgent.addLifecycleListener(new AgentListener());
    }

    /////////////////////////////////////
    // Implements DockerBase
    /////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "destcity":
                host = kv.value;
                break;

            case "destport":
                port= Integer.parseInt(kv.value);
                break;

            case "desttown":
                warpBase = kv.value;
                if (!warpBase.endsWith("/"))
                    warpBase += "/";
                break;

            case "maxships":
                maxShips = Integer.parseInt(kv.value);
                break;

            case "timeout":
                timeoutSec = Integer.parseInt(kv.value);
                break;

        }
        return true;
    }

    /////////////////////////////////////
    // Implements Club
    /////////////////////////////////////


    @Override
    public void arrive(Tour tour) throws HttpException {

        GrandAgent agt = GrandAgent.get(tour.ship.agentId);

        // Subclass hook: a multiplex-capable docker can return an existing
        // shared WarpShip here. When non-null, skip the rent path entirely.
        WarpShip reused = pickReusableShip(agt, tour);
        if (reused != null) {
            try {
                synchronized (tourList) {
                    tourList.add(tour);
                }
                reused.startWarpTour(tour);
            }
            catch (IOException e) {
                BayLog.error(e);
                throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
            }
            return;
        }

        WarpShipStore sto = getShipStore(agt.agentId);

        WarpShip wsip = sto.rent();
        if(wsip == null) {
            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "WarpDocker busy");
        }

        try {
            BayLog.trace("%s got from store", wsip);
            boolean needConnect = false;
            Transporter tp = null;
            if (!wsip.initialized) {
                NetworkChannelRudder rd;

                if(agt.netMultiplexer.useAsyncAPI()) {
                    AsynchronousSocketChannel ch;
                    if(hostAddr instanceof InetSocketAddress)
                        ch = AsynchronousSocketChannel.open();
                    else
                        throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, "Asynchronous mode not supported for UNIX domain socket");
                    if (hostAddr instanceof InetSocketAddress) {
                        ch.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        applyQuickAck(ch);
                    }
                    rd = new AsynchronousSocketChannelRudder(ch);
                }
                else {
                    SocketChannel ch;
                    if(hostAddr instanceof InetSocketAddress)
                        ch = SocketChannel.open();
                    else
                        ch = SysUtil.openUnixDomainSocketChannel();
                    if (hostAddr instanceof InetSocketAddress) {
                        ch.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
                        applyQuickAck(ch);
                    }
                    rd = new SocketChannelRudder(ch);
                }

                tp = newTransporter(agt, rd, wsip);

                ProtocolHandler protoHnd = ProtocolHandlerStore.getStore(protocol(), false, agt.agentId).rent();
                wsip.initWarp(rd, agt.agentId, tp, WarpBase.this, protoHnd);

                BayLog.debug("%s init warp ship", wsip);
                needConnect = true;
            }

            synchronized (tourList) {
                tourList.add(tour);
            }

            wsip.startWarpTour(tour);

            // Subclass hook: notify after a fresh ship has been rented and
            // attached to its first tour. Used by multiplex-capable dockers
            // to register the ship in their reuse pool.
            onShipRented(agt, wsip);

            if(needConnect) {
                RudderState st = RudderStateStore.getStore(agt.agentId).rent();
                st.init(wsip.rudder, tp);
                // Mark this state so the multiplexer re-arms TCP_QUICKACK
                // after each read on this upstream socket. Backends like
                // php-fpm leave Nagle on, so without QUICKACK the proxy's
                // delayed-ACK timer adds 40 ms per response in the few-MTU
                // body range. Skip for unix-domain sockets.
                st.quickAck = (hostAddr instanceof InetSocketAddress);
                agt.netMultiplexer.addRudderState(wsip.rudder, st);
                agt.netMultiplexer.getTransporter(wsip.rudder).reqConnect(wsip.rudder, hostAddr);
            }

        }
        catch(IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
    }

    /////////////////////////////////////
    // Subclass hooks for multiplex-aware dockers
    /////////////////////////////////////

    /**
     * Subclasses override to return an existing WarpShip that can carry an
     * additional tour (= H2 stream multiplex). Returning non-null causes
     * arrive() to skip the rent / connect path and just start the tour on
     * the returned ship. Default returns null = always rent fresh.
     */
    protected WarpShip pickReusableShip(GrandAgent agt, Tour tour) {
        return null;
    }

    /**
     * Subclasses are notified immediately after arrive() rents a fresh
     * WarpShip and starts its first tour. Use to register the ship in a
     * reuse pool. Default no-op.
     */
    protected void onShipRented(GrandAgent agt, WarpShip wsip) {
    }

    /////////////////////////////////////
    // Implements Warp
    /////////////////////////////////////

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String warpBase() {
        return warpBase;
    }

    @Override
    public int timeoutSec() {
        return timeoutSec;
    }

    @Override
    public void keep(Ship warpShip) {
        BayLog.debug("%s keep warp ship: %s", this, warpShip);
        getShipStore(warpShip.agentId).keep((WarpShip) warpShip);
    }

    @Override
    public void onEndShip(Ship warpShip) {
        BayLog.debug("%s Return protocol handler: ", warpShip);
        getProtocolHandlerStore(warpShip.agentId).Return(((WarpShip)warpShip).protocolHandler);
        BayLog.debug("%s return warp ship", warpShip);
        getShipStore(warpShip.agentId).Return((WarpShip) warpShip);
    }

    /////////////////////////////////////
    // Other methods
    /////////////////////////////////////


    public WarpShipStore getShipStore(int agtId) {
        return stores.get(agtId);
    }


    //////////////////////////////////////////////////////
    // Private methods
    //////////////////////////////////////////////////////

    private void startWarpTour(WarpShip wsip, Tour tour) throws IOException {
        synchronized (tourList) {
            tourList.add(tour);
        }

        wsip.startWarpTour(tour);
    }

    private ProtocolHandlerStore getProtocolHandlerStore(int agtId) {
        return ProtocolHandlerStore.getStore(protocol(), false, agtId);
    }
}
