package baykit.bayserver.docker.warp;

import baykit.bayserver.*;
import baykit.bayserver.agent.ChannelListener;
import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.docker.base.InboundDataListener;
import baykit.bayserver.protocol.ProtocolHandler;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.base.ClubBase;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.StringUtil;
import baykit.bayserver.util.SysUtil;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class WarpDocker extends ClubBase {

    class AgentListener implements GrandAgent.GrandAgentLifecycleListener {

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

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    public abstract boolean secure();
    protected abstract String protocol();
    protected abstract Transporter newTransporter(GrandAgent agent, SocketChannel ch) throws IOException;

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

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

    //////////////////////////////////////////////////////
    // Implements DockerBase
    //////////////////////////////////////////////////////

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

    //////////////////////////////////////////////////////
    // Implements Club
    //////////////////////////////////////////////////////


    @Override
    public void arrive(Tour tour) throws HttpException {

        GrandAgent agt = tour.ship.agent;
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
                wsip.initWarp(ch, agt, tp, this, protoHnd);
                tp.init(agt.nonBlockingHandler, ch, new WarpDataListener(wsip));
                BayLog.debug("%s init warp ship", wsip);
                needConnect = true;
            }

            synchronized (tourList) {
                tourList.add(tour);
            }

            wsip.startWarpTour(tour);

            if(needConnect) {
                agt.nonBlockingHandler.addChannelListener(wsip.ch, tp);
                agt.nonBlockingHandler.askToConnect((SocketChannel)wsip.ch, hostAddr);
            }

        }
        catch(IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
    }


    //////////////////////////////////////////////////////
    // Other methods
    //////////////////////////////////////////////////////

    public void keepShip(WarpShip wsip) {
        BayLog.debug("%s keep warp ship: %s", this, wsip);
        getShipStore(wsip.agent.agentId).keep(wsip);
    }

    public void returnShip(WarpShip wsip) {
        BayLog.debug("%s return warp ship: %s", this, wsip);
        getShipStore(wsip.agent.agentId).Return(wsip);
    }

    public void returnProtocolHandler(GrandAgent agt, ProtocolHandler protoHnd) {
        BayLog.debug("%s Return protocol handler: ", protoHnd);
        getProtocolHandlerStore(agt.agentId).Return(protoHnd);
    }

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
