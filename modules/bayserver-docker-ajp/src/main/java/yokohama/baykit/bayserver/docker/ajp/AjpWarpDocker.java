package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;

public class AjpWarpDocker extends WarpBase implements AjpDocker {


    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements WarpDocker
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean secure() {
        return false;
    }

    @Override
    protected String protocol() {
        return PROTO_NAME;
    }

    @Override
    protected PlainTransporter newTransporter(GrandAgent agent, NetworkChannelRudder rd, Ship sip) throws IOException {
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
