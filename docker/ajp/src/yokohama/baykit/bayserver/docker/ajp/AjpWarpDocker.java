package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.docker.warp.WarpDocker;
import yokohama.baykit.bayserver.agent.transporter.PlainTransporter;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.util.IOUtil;

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
    // Implements WarpDocker
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
