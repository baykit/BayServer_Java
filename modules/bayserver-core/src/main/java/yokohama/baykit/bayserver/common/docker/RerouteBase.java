package yokohama.baykit.bayserver.common.docker;

import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Reroute;

public abstract class RerouteBase extends DockerBase implements Reroute {

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        String name = elm.arg;
        if(!name.equals("*"))
            throw new ConfigException(elm.fileName, elm.lineNo, "Invalid reroute name: " + name);

        super.init(elm, parent);
    }



    protected boolean match(String uri) {
        return true;
    }
}
