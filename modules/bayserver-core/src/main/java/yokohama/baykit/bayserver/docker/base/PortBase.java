package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.common.Cities;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.common.InboundShipStore;
import yokohama.baykit.bayserver.docker.*;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;

public abstract class PortBase extends DockerBase implements Port {

    final ArrayList<Permission> permissionList = new ArrayList<>();
    public String host;
    public int port;
    public String socketPath;
    public int timeoutSec = -1; // -1 means "Use socketTimeout of Harbor docker"
    public Secure secureDocker;
    boolean anchored = true;
    ArrayList<String[]> additionalHeaders = new ArrayList<>();
    private Cities cities = new Cities();
    private int socketBufferSize = -1;

    ///////////////////////////////////////////////////////////////////////
    // abstract methods
    ///////////////////////////////////////////////////////////////////////

    abstract protected boolean supportAnchored();
    abstract protected boolean supportUnanchored();

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        if(StringUtil.empty(elm.arg))
            throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_INVALID_PORT_NAME, elm.name));
        super.init(elm, parent);

        String portName = elm.arg.toLowerCase();
        if(portName.startsWith(":unix:")) {
            // unix domain sokcet
            if(!SysUtil.supportUnixDomainSocketAddress()) {
                throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_CANNOT_SUPPORT_UNIX_DOMAIN_SOCKET));
            }
            port = -1;
            socketPath = elm.arg.substring(6);
            host = elm.arg;
        }
        else {
            // TCP or UDP port
            String hostPort;
            if(portName.startsWith(":tcp:")) {
                // tcp server socket
                anchored = true;
                hostPort = elm.arg.substring(5);
            }
            else if(portName.startsWith(":udp:")) {
                // udp server socket
                anchored = false;
                hostPort = elm.arg.substring(5);
            }
            else {
                // default: tcp server socket
                anchored = true;
                hostPort = elm.arg;
            }
            int idx = hostPort.indexOf(':');

            try {
                if (idx >= 0) {
                    host = hostPort.substring(0, idx);
                    port = Integer.parseInt(hostPort.substring(idx + 1));
                }
                else {
                    host = null;
                    port = Integer.parseInt(hostPort);
                }
            }
            catch(NumberFormatException e) {
                throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_INVALID_PORT_NAME, elm.arg));
            }
        }

        // TCP/UDP support check
        if(anchored) {
            if (!supportAnchored())
                throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_TCP_NOT_SUPPORTED));
        }
        else {
            if (!supportUnanchored())
                throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_UDP_NOT_SUPPORTED));
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////

    @Override
    public boolean initDocker(Docker dkr) throws ConfigException {
        if(dkr instanceof Permission) {
            permissionList.add((Permission)dkr);
        }
        else if(dkr instanceof City) {
            cities.add((City)dkr);
        }
        else if(dkr instanceof Secure) {
            secureDocker = (Secure)dkr;
        }
        else {
            return false;
        }
        return true;
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "timeout":
                timeoutSec = Integer.parseInt(kv.value);
                break;

            case "addheader": {
                int idx = kv.value.indexOf(':');
                if (idx < 0) {
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
                String name = kv.value.substring(0, idx).trim();
                String value = kv.value.substring(idx + 1).trim();
                additionalHeaders.add(new String[]{name, value});
                break;
            }
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements Port
    ///////////////////////////////////////////////////////////////////////
    @Override
    public String host() {
        return host;
    }

    @Override
    public final int port() {
        return port;
    }

    @Override
    public String socketPath() {
        return socketPath;
    }

    @Override
    public SocketAddress address() throws IOException {
        if(socketPath != null) {
            // Unix domain socket
            return SysUtil.getUnixDomainSocketAddress(socketPath);
        }
        if(host == null) {
            return new InetSocketAddress(port);
        }
        else {
            return new InetSocketAddress(host, port);
        }
    }

    @Override
    public boolean anchored() {
        return anchored;
    }

    @Override
    public final int timeoutSec() {
        return timeoutSec;
    }

    @Override
    public final boolean secure() {
        return secureDocker != null;
    }

    @Override
    public ArrayList<String[]> additionalHeaders() {
        return additionalHeaders;
    }

    @Override
    public Collection<City> cities() {
        return cities.cities();
    }

    @Override
    public City findCity(String name) {
        return cities.findCity(name);
    }

    @Override
    public void onConnected(int agentId, Rudder rd) throws HttpException {

        checkAdmitted((NetworkChannelRudder) rd);

        InboundShip sip = getShipStore(agentId).rent();
        Transporter tp;
        GrandAgent agt = GrandAgent.get(agentId);

        if(secure()) {
            tp = secureDocker.newTransporter(agentId, sip);
        }
        else {
            if(socketBufferSize < 0) {
                try {
                    socketBufferSize = ((NetworkChannelRudder)rd).getSocketReceiveBufferSize();
                }
                catch(IOException e) {
                    socketBufferSize = 8192;
                }
            }

            tp = new PlainTransporter(
                    agt.netMultiplexer,
                    sip,
                    true,
                    socketBufferSize,
                    false);
            tp.init();
        }

        ProtocolHandler protoHnd = getProtocolHandlerStore(protocol(), agentId).rent();
        sip.initInbound(rd, agentId, tp, this, protoHnd);

        RudderState st = new RudderState(rd, tp);
        agt.netMultiplexer.addRudderState(rd, st);
        agt.netMultiplexer.reqRead(rd);
    }

    @Override
    public final void returnProtocolHandler(int agentId, ProtocolHandler protoHnd) {
        BayLog.debug("%s Return protocol handler: ", protoHnd);
        getProtocolHandlerStore(protoHnd.protocol(), agentId).Return(protoHnd);
    }

    @Override
    public final void returnShip(InboundShip sip) {
        BayLog.debug("%s Return ship: ", sip);
        getShipStore(sip.agentId).Return(sip);
    }

    ///////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////
    private void checkAdmitted(NetworkChannelRudder rd) throws HttpException {
        for(Permission p : permissionList) {
            p.socketAdmitted(rd);
        }
    }

    protected static InboundShipStore getShipStore(int agentId) {
        return InboundShipStore.getStore(agentId);
    }

    protected static ProtocolHandlerStore getProtocolHandlerStore(String protocol, int agentId) {
        return ProtocolHandlerStore.getStore(protocol, true, agentId);
    }
}
