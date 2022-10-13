package baykit.bayserver.docker.cgi;

import baykit.bayserver.*;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.util.CGIUtil;

import java.io.IOException;
import java.util.Map;

public class PhpCgiDocker extends CgiDocker {

    public static final String PHP_SELF = "PHP_SELF";

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(interpreter == null)
            interpreter = "php-cgi";

        BayLog.info("PHP interpreter: " + interpreter);
    }

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
