package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.common.docker.PortBase;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;

public class AjpPortDocker extends PortBase implements AjpDocker {

    /////////////////////////////////////////////////////////////////////////////
    // Implements Port
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public String protocol() {
        return PROTO_NAME;
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
                PROTO_NAME,
                new AjpPacketFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                PROTO_NAME,
                true,
                new AjpInboundHandler.InboundProtocolHandlerFactory());
    }
}
