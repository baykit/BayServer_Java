package yokohama.baykit.bayserver.docker.cgi;

import baykit.bayserver.*;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.util.CGIUtil;
import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.ConfigException;

import java.io.IOException;
import java.util.Map;

public class PhpCgiDocker extends CgiDocker {

    public static final String PHP_SELF = "PHP_SELF";

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(interpreter == null)
            interpreter = "php-cgi";

        BayLog.info("PHP interpreter: " + interpreter);
    }

    //////////////////////////////////////////////////////
    // Implements CgiDocker
    //////////////////////////////////////////////////////

    @Override
    protected Process createProcess(Map<String, String> env) throws IOException {
        env.put(PHP_SELF, env.get(CGIUtil.SCRIPT_NAME));
        ProcessBuilder pb = new ProcessBuilder(interpreter);
        Map<String, String> map = pb.environment();
        map.clear();
        map.putAll(env);
        map.put("REDIRECT_STATUS", Integer.toString(200));
        //map.put("LANG", "C");
        return pb.start();
    }
}
