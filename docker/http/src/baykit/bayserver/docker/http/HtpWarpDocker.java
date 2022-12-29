package baykit.bayserver.docker.http;

import baykit.bayserver.*;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.agent.transporter.PlainTransporter;
import baykit.bayserver.agent.transporter.SecureTransporter;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.http.h1.*;
import baykit.bayserver.docker.http.h2.*;
import baykit.bayserver.docker.warp.WarpDocker;
import baykit.bayserver.util.IOUtil;
import baykit.bayserver.util.StringUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class HtpWarpDocker extends WarpDocker implements HtpDocker {

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

    public boolean secure;
    public boolean supportH2 = true;
    SSLContext sslCtx;


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

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

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "supporth2":
                supportH2 = StringUtil.parseBool(kv.value);
                break;

            case "secure":
                secure = StringUtil.parseBool(kv.value);
                break;
        }
        return true;
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements WarpDocker
    //////////////////////////////////////////////////////////////////////////////////////////
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
    protected Transporter newTransporter(GrandAgent agent, SocketChannel ch) throws IOException {
        if(secure) {
            String[] appProtocols = supportH2 ? new String[]{"h2"} : null;
            return new SecureTransporter(sslCtx, appProtocols, false, true);
        }
        else
            return new PlainTransporter(false, IOUtil.getSockRecvBufSize(ch));
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
