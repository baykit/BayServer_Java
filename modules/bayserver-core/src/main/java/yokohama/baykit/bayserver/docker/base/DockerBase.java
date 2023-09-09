package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.BcfObject;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.*;

public abstract class DockerBase implements Docker {

    protected String type;

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////
    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        type = elm.name;
        for (BcfObject o : elm.contentList) {
            if (o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal) o;
                try {
                    if (!initKeyVal(kv))
                        throw new ConfigException(o.fileName, o.lineNo, BayMessage.CFG_INVALID_PARAMETER(kv.key));
                }
                catch(ConfigException e) {
                    throw e;
                }
                catch(NumberFormatException e) {
                    // Number property failed
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_PARAMETER_IS_NOT_A_NUMBER(kv.key, kv.value));
                }
                catch(Exception e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, e.getMessage());
                }
            } else {
                BcfElement element = (BcfElement) o;
                Docker dkr;
                try {
                    dkr = BayDockers.createDocker(element, this);
                } catch (ConfigException e) {
                    throw e;
                } catch (BayException e) {
                    BayLog.error(e);
                    throw new ConfigException(element.fileName, element.lineNo, BayMessage.CFG_INVALID_DOCKER(element.name));
                }

                if (!initDocker(dkr))
                    throw new ConfigException(o.fileName, o.lineNo, BayMessage.CFG_INVALID_DOCKER(((BcfElement) o).name));
            }
        }
    }

    @Override
    public final String type() {
        return type;
    }

    ///////////////////////////////////////////////////////////////////////
    // Base methods
    ///////////////////////////////////////////////////////////////////////

    public boolean initDocker(Docker dkr) throws ConfigException {
        return false;
    }

    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return false;

            case "docker":
                return true;
        }
    }
}
