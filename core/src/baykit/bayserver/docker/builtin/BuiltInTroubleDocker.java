package baykit.bayserver.docker.builtin;

import baykit.bayserver.BayMessage;
import baykit.bayserver.ConfigException;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.Trouble;
import baykit.bayserver.docker.base.DockerBase;

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
