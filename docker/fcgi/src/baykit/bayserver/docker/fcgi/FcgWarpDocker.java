package baykit.bayserver.docker.fcgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.ConfigException;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.agent.transporter.PlainTransporter;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.warp.WarpDocker;
import baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class FcgWarpDocker extends WarpDocker implements FcgDocker {

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
    protected Transporter newTransporter(GrandAgent agent, SocketChannel ch) throws IOException {
        return new PlainTransporter(false, IOUtil.getSockRecvBufSize(ch));
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
