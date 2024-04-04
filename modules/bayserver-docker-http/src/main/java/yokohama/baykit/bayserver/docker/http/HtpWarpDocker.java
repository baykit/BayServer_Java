package yokohama.baykit.bayserver.docker.http;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.SecureTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.docker.http.h1.H1PacketFactory;
import yokohama.baykit.bayserver.docker.http.h1.H1WarpHandler;
import yokohama.baykit.bayserver.docker.http.h2.H2PacketFactory;
import yokohama.baykit.bayserver.docker.http.h2.H2WarpHandler;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.StringUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

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
    boolean traceSSL = false;
    SSLContext sslCtx;

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
        return H1_PROTO_NAME;
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

    static {
        PacketStore.registerProtocol(
                H1_PROTO_NAME,
                new H1PacketFactory()
        );
        PacketStore.registerProtocol(
                H2_PROTO_NAME,
                new H2PacketFactory()
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
