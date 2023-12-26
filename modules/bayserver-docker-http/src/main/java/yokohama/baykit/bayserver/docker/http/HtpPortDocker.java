package yokohama.baykit.bayserver.docker.http;


import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.common.docker.PortBase;
import yokohama.baykit.bayserver.docker.http.h1.H1InboundHandler;
import yokohama.baykit.bayserver.docker.http.h1.H1PacketFactory;
import yokohama.baykit.bayserver.docker.http.h2.H2ErrorCode;
import yokohama.baykit.bayserver.docker.http.h2.H2InboundHandler;
import yokohama.baykit.bayserver.docker.http.h2.H2PacketFactory;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.util.StringUtil;

public class HtpPortDocker extends PortBase implements HtpDocker {

    public static final boolean DEFAULT_SUPPORT_HTTP2 = true;

    public boolean supportH2 = DEFAULT_SUPPORT_HTTP2;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(supportH2) {
            if(secure())
                secureDocker.setAppProtocols(new String[]{"h2", "http/1.1"});
            H2ErrorCode.init();
        }
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            case "supporth2":
            case "enableh2":
                supportH2 = StringUtil.parseBool(kv.value);
                break;

            default:
                return super.initKeyVal(kv);
        }
        return true;
    }

    /////////////////////////////////////////////////////////////////////////////
    // Implements Port
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public String protocol() {
        return H1_PROTO_NAME;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements PortBase
    ///////////////////////////////////////////////////////////////////////
    @Override
    protected boolean supportAnchored() {
        return true;
    }

    @Override
    protected boolean supportUnanchored() {
        return false;
    }

    /////////////////////////////////////////////////////////////////////////////
    // private methods
    /////////////////////////////////////////////////////////////////////////////
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
                true,
                new H1InboundHandler.InboundProtocolHandlerFactory());
        ProtocolHandlerStore.registerProtocol(
                H2_PROTO_NAME,
                true,
                new H2InboundHandler.InboundProtocolHandlerFactory());
    }
}
