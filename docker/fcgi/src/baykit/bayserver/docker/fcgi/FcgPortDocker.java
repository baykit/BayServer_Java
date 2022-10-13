package baykit.bayserver.docker.fcgi;

import baykit.bayserver.docker.base.PortBase;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.protocol.ProtocolHandlerStore;

public class FcgPortDocker extends PortBase implements FcgDocker{

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
                new FcgPacketFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                PROTO_NAME,
                true,
                new FcgInboundHandler.InboundProtocolHandlerFactory());
    }
}
