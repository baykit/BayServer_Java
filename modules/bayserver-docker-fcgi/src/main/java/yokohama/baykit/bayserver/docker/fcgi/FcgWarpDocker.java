package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;

public class FcgWarpDocker extends WarpBase implements FcgDocker {

    public String scriptBase;
    public String docRoot;

    //////////////////////////////////////////////////////////////////////////////////////////
    // Implements Docker                                                                    //
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if (scriptBase == null)
            BayLog.warn("docRoot is not specified");
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "scriptbase":
                scriptBase = kv.value;
                break;

            case "docroot":
                docRoot = kv.value;
                break;
        }
        return true;
    }

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
    protected PlainTransporter newTransporter(GrandAgent agt, NetworkChannelRudder rd, Ship sip) throws IOException {
        PlainTransporter tp =
                new PlainTransporter(
                        agt.netMultiplexer,
                        sip,
                        false,
                        rd.getSocketReceiveBufferSize(),
                        false);
        return tp;
    }

    static {
        PacketStore.registerProtocol(
                PROTO_NAME,
                new FcgPacketFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                PROTO_NAME,
                false,
                new FcgWarpHandler.WarpProtocolHandlerFactory());
    }

}
