package baykit.bayserver.docker.base;

import baykit.bayserver.ConfigException;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.Reroute;
import baykit.bayserver.docker.base.DockerBase;

public abstract class RerouteBase extends DockerBase implements Reroute {

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
