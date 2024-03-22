package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class AjpWarpDocker extends WarpBase implements AjpDocker {


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
    protected PlainTransporter newTransporter(GrandAgent agent, SocketChannel ch, Ship sip) throws IOException {
        PlainTransporter tp = new PlainTransporter(agent.netMultiplexer, sip,false, IOUtil.getSockRecvBufSize(ch), false);
        tp.init();
        return tp;
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
