package yokohama.baykit.bayserver.docker.http;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.SecureTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.docker.http.h1.H1CommandFactory;
import yokohama.baykit.bayserver.docker.http.h1.H1PacketFactory;
import yokohama.baykit.bayserver.docker.http.h1.H1WarpHandler;
import yokohama.baykit.bayserver.docker.http.h2.H2CommandFactory;
import yokohama.baykit.bayserver.docker.http.h2.H2PacketFactory;
import yokohama.baykit.bayserver.docker.http.h2.H2WarpHandler;
import yokohama.baykit.bayserver.protocol.CommandStore;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.StringUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class HtpWarpDocker extends WarpBase implements HtpDocker {

    static class DummyTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            BayLog.debug("checkClientTrusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            BayLog.debug("checkServerTrusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }


    public static final String DEFAULT_SSL_PROTOCOL = "TLS";

    boolean secure;
    boolean supportH2 = true;
    // When true, the warp uses H2 from the start (h2c on cleartext, h2 on
    // TLS without ALPN negotiation). Defaults to false so existing httpWarp
    // configs keep their H1 behaviour.
    boolean enableH2 = false;
    boolean traceSSL = false;
    SSLContext sslCtx;

    /**
     * Per-agent pool of shared backend WarpShips. Only populated when
     * enableH2 is true; otherwise null (and arrive() falls through to the
     * exclusive rent path inherited from WarpBase).
     */
    final Map<Integer, WarpShipPool> pools = new HashMap<>();

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(secure) {
            try {
                sslCtx = SSLContext.getInstance(DEFAULT_SSL_PROTOCOL);
                sslCtx.init(null, new TrustManager[] {new DummyTrustManager()}, null);
            } catch (Exception e) {
                BayLog.error(e);
                throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_SSL_INIT_ERROR), e);
            }
        }

        if (enableH2) {
            // The base WarpBase already registered a listener that owns the
            // per-agent WarpShipStore. We add a second one here, scoped to
            // multiplex pools. Adding listeners after the base init is
            // intentional so the pool entry exists for any agent that gets
            // spawned during runtime.
            GrandAgent.addLifecycleListener(new LifecycleListener() {
                @Override
                public void add(int agentId) {
                    pools.put(agentId, new WarpShipPool());
                }

                @Override
                public void remove(int agentId) {
                    pools.remove(agentId);
                }
            });
        }
    }

    //////////////////////////////////////////////////////
    // Implements DockerBase
    //////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "supporth2":
                supportH2 = StringUtil.parseBool(kv.value);
                break;

            case "enableh2":
                enableH2 = StringUtil.parseBool(kv.value);
                break;

            case "tracessl":
                traceSSL = StringUtil.parseBool(kv.value);
                break;

            case "secure":
                secure = StringUtil.parseBool(kv.value);
                break;
        }
        return true;
    }

    //////////////////////////////////////////////////////
    // Implements WarpDocker
    //////////////////////////////////////////////////////

    @Override
    public boolean secure() {
        return secure;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements WarpDockerBase
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected String protocol() {
        // When enableh2 is set, the warp speaks HTTP/2 from the start:
        //  - secure=false  -> h2c (cleartext H2 with prior knowledge); the
        //    H2WarpHandler emits the connection preface + initial SETTINGS
        //    on the first sendReqHeaders call.
        //  - secure=true   -> h2 over TLS *without* ALPN negotiation. Most
        //    servers also accept this when their listener is h2-enabled.
        return enableH2 ? H2_PROTO_NAME : H1_PROTO_NAME;
    }

    @Override
    protected PlainTransporter newTransporter(GrandAgent agent, NetworkChannelRudder rd, Ship sip) throws IOException {
        if(secure) {
            String[] appProtocols = supportH2 ? new String[]{"h2"} : null;
            SecureTransporter tp =
                    new SecureTransporter(
                            agent.netMultiplexer,
                            sip,
                            false,
                            -1,
                            traceSSL,
                            sslCtx,
                            appProtocols);
            tp.init();
            return tp;
        }
        else {
            PlainTransporter tp =
                    new PlainTransporter(
                            agent.netMultiplexer,
                            sip,
                            false,
                            rd.getSocketReceiveBufferSize(),
                            false);
            tp.init();
            return tp;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Multiplex hooks (only active when enableH2 is true)
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected WarpShip pickReusableShip(GrandAgent agt, Tour tour) {
        if (!enableH2) return null; // exclusive (rent) path for H1 / non-mux
        WarpShipPool pool = pools.get(agt.agentId);
        return (pool != null) ? pool.findIdlest() : null;
    }

    @Override
    protected void onShipRented(GrandAgent agt, WarpShip wsip) {
        if (!enableH2) return;
        WarpShipPool pool = pools.get(agt.agentId);
        if (pool != null) pool.add(wsip);
    }

    @Override
    public void keep(Ship warpShip) {
        if (enableH2) {
            // Multiplex mode: ship stays in the per-agent pool until the
            // backend connection actually closes. Returning it to the
            // WarpShipStore.keepList here would yank the ship out of the
            // pool's list of reusable shared connections.
            return;
        }
        super.keep(warpShip);
    }

    @Override
    public void onEndShip(Ship warpShip) {
        if (enableH2) {
            WarpShipPool pool = pools.get(warpShip.agentId);
            if (pool != null) pool.remove((WarpShip) warpShip);
        }
        super.onEndShip(warpShip);
    }

    @Override
    public void excludeFromPool(Ship warpShip) {
        if (!enableH2) return;
        WarpShipPool pool = pools.get(warpShip.agentId);
        if (pool != null) pool.remove((WarpShip) warpShip);
    }

    static {
        PacketStore.registerProtocol(
                H1_PROTO_NAME,
                new H1PacketFactory()
        );
        PacketStore.registerProtocol(
                H2_PROTO_NAME,
                new H2PacketFactory()
        );
        CommandStore.registerProtocol(
                H1_PROTO_NAME,
                new H1CommandFactory()
        );
        CommandStore.registerProtocol(
                H2_PROTO_NAME,
                new H2CommandFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                H1_PROTO_NAME,
                false,
                new H1WarpHandler.WarpProtocolHandlerFactory());
        ProtocolHandlerStore.registerProtocol(
                H2_PROTO_NAME,
                false,
                new H2WarpHandler.WarpProtocolHandlerFactory());
    }
}
