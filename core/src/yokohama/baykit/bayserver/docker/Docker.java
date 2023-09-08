package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.bcf.BcfElement;

public interface Docker {
    void init(BcfElement ini, Docker parent) throws ConfigException;

    String type();
}
