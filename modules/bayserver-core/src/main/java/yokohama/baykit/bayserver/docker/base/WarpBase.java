package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
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
                    rd = new AsynchronousSocketChannelRudder(ch);
                }
                else {
                    SocketChannel ch;
                    if(hostAddr instanceof InetSocketAddress)
                        ch = SocketChannel.open();
                    else
                        ch = SysUtil.openUnixDomainSocketChannel();
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

            if(needConnect) {
                agt.netMultiplexer.addRudderState(wsip.rudder, new RudderState(wsip.rudder, tp));
                agt.netMultiplexer.getTransporter(wsip.rudder).reqConnect(wsip.rudder, hostAddr);
            }

        }
        catch(IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
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
