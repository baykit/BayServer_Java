package baykit.bayserver.docker;

import baykit.bayserver.ConfigException;
import baykit.bayserver.bcf.BcfElement;

public interface Docker {
    void init(BcfElement ini, Docker parent) throws ConfigException;

    String type();
}
