package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.MultiplexingValve;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.PortBase;
import yokohama.baykit.bayserver.docker.builtin.BuiltInSecureDocker;
import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Quiche;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;

public class H3PortDocker extends PortBase implements H3Docker {

    Config config;
    String appProtocols[] = {
            "h3",
            "h3-29",
            "h3-28",
            "h3-27"
    };

    ////////////////////////////////////////////
    // Implements Docker
    ////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        File cert = ((BuiltInSecureDocker)secureDocker).certFile;
        if(cert == null)
            throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_SSL_CERT_FILE_NOT_SPECIFIED));
        File key = ((BuiltInSecureDocker)secureDocker).keyFile;
        if(key == null)
            throw new ConfigException(elm.fileName, elm.lineNo, BayMessage.get(Symbol.CFG_SSL_KEY_FILE_NOT_SPECIFIED));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for(String proto: appProtocols) {
            try {
                out.write(proto.length());
                out.write(proto.getBytes());
            }
            catch(IOException e) {
                BayLog.error(e);
            }
        }

        this.config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
                .withApplicationProtos(out.toByteArray())
                .withVerifyPeer(false)
                .loadCertChainFromPemFile(cert.getPath())
                .loadPrivKeyFromPemFile(key.getPath())
                .withMaxIdleTimeout(5_000)
                .withMaxUdpPayloadSize(QicPacket.MAX_DATAGRAM_SIZE)
                .withInitialMaxData(10_000_000)
                .withInitialMaxStreamDataBidiLocal(1_000_000)
                .withInitialMaxStreamDataBidiRemote(1_000_000)
                .withInitialMaxStreamDataUni(1_000_000)
                .withInitialMaxStreamsBidi(4)
                .withInitialMaxStreamsUni(4)
                .withDisableActiveMigration(true)
                .enableEarlyData()
                .build();
    }

    ////////////////////////////////////////////
    // Implements Port
    ////////////////////////////////////////////

    @Override
    public String protocol() {
        return PROTO_NAME;
    }

    ////////////////////////////////////////////
    // Implements PortBase
    ////////////////////////////////////////////
    @Override
    protected boolean supportAnchored() {
        return false;
    }

    @Override
    protected boolean supportUnanchored() {
        return true;
    }

    @Override
    public ChannelListener newChannelListener(int agentId, SelectableChannel ch) {
        QicDataListener lis = new QicDataListener();
        UdpTransporter tp = new UdpTransporter(true, 8192);
        lis.initUdp((DatagramChannel) ch, agentId, tp, this);
        GrandAgent agt = GrandAgent.get(agentId);
        tp.init(ch, lis, new MultiplexingValve(agt.multiplexer, ch));
        return tp;
    }

    ////////////////////////////////////////////
    // private methods
    ////////////////////////////////////////////

}
