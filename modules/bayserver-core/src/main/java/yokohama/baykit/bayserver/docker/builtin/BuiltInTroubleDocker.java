package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Trouble;
import yokohama.baykit.bayserver.common.docker.DockerBase;

import java.util.HashMap;
import java.util.Map;

public class BuiltInTroubleDocker extends DockerBase implements Trouble {

    Map<Integer, Command> cmdMap = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        int status = Integer.parseInt(kv.key);

        int pos = kv.value.indexOf(' ');
        if(pos <= 0) {
            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER(kv.key));
        }

        String mstr = kv.value.substring(0, pos);
        Method method;
        if(mstr.equalsIgnoreCase("guide"))
            method = Method.GUIDE;
        else if(mstr.equalsIgnoreCase("text"))
            method = Method.TEXT;
        else if(mstr.equalsIgnoreCase("reroute"))
            method = Method.REROUTE;
        else {
            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER(kv.key));
        }
        cmdMap.put(status, new Command(method, kv.value.substring(pos + 1)));
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements Trouble
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Command find(int status) {
        return cmdMap.get(status);
    }
}
