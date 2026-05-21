package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.WarpBase;
import yokohama.baykit.bayserver.protocol.CommandStore;
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

    /**
     * Decorate the SCRIPT_FILENAME FCGI parameter before sending it to
     * the upstream. The default `proxy:fcgi://host:port` prefix
     * disarms php-fpm's defensive URL-reinterpretation of the value
     * (see php.net/manual/en/security.cgi-bin.attack.php), so it's
     * the right default for any php-fpm-style backend. Subclasses that
     * talk to a backend without that quirk (= PhpVerseDocker / phpverse,
     * which just uses SCRIPT_FILENAME as a filesystem path) override
     * to return the raw path.
     */
    public String decorateScriptFilename(String scriptFname) {
        return "proxy:fcgi://" + host + ":" + port + scriptFname;
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
        CommandStore.registerProtocol(
                PROTO_NAME,
                new FcgCommandFactory()
        );
        ProtocolHandlerStore.registerProtocol(
                PROTO_NAME,
                false,
                new FcgWarpHandler.WarpProtocolHandlerFactory());
    }

}
