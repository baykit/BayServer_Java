package baykit.bayserver.docker.ajp;

import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.docker.warp.WarpDocker;
import baykit.bayserver.agent.transporter.PlainTransporter;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class AjpWarpDocker extends WarpDocker implements AjpDocker {


    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements WarpDocker
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean secure() {
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements WarpDockerBase
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected String protocol() {
        return PROTO_NAME;
    }

    @Override
    protected Transporter newTransporter(GrandAgent agent, SocketChannel ch) throws IOException {
        return new PlainTransporter(false, IOUtil.getSockRecvBufSize(ch));
    }

    static {
        PacketStore.registerProtocol(
                PROTO_NAME,
                new AjpPacketFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                PROTO_NAME,
                false,
                new AjpWarpHandler.WarpProtocolHandlerFactory());
    }

}
