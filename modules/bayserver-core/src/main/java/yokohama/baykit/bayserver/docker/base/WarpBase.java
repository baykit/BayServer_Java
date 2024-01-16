package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.MultiplexingValve;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.common.Valve;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.common.WarpShipStore;
import yokohama.baykit.bayserver.docker.Warp;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;
import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;

import java.io.IOException;
import java.net.*;
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
    protected abstract Transporter newTransporter(GrandAgent agent, SocketChannel ch) throws IOException;

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
                SocketChannel ch;
                if(hostAddr instanceof InetSocketAddress)
                    ch = SocketChannel.open();
                else
                    ch = SysUtil.openUnixDomainSocketChannel();

                ch.configureBlocking(false);
                tp = newTransporter(agt, ch);
                ProtocolHandler protoHnd = ProtocolHandlerStore.getStore(protocol(), false, agt.agentId).rent();
                Valve v = new MultiplexingValve(agt.multiplexer, ch);
                wsip.initWarp(ch, agt.agentId, tp, v, this, protoHnd);

                tp.init(ch, new SimpleDataListener(wsip), v);
                BayLog.debug("%s init warp ship", wsip);
                needConnect = true;
            }

            synchronized (tourList) {
                tourList.add(tour);
            }

            wsip.startWarpTour(tour);

            if(needConnect) {
                agt.multiplexer.addChannelListener(wsip.ch, tp);
                agt.multiplexer.reqConnect((SocketChannel)wsip.ch, hostAddr);
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
    public void onEndTour(Ship warpShip) {
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

    private ProtocolHandlerStore getProtocolHandlerStore(int agtId) {
        return ProtocolHandlerStore.getStore(protocol(), false, agtId);
    }
}
