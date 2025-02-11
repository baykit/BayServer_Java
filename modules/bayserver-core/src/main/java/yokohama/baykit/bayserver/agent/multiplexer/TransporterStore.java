package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.util.ObjectFactory;
import yokohama.baykit.bayserver.util.ObjectStore;
import yokohama.baykit.bayserver.util.StringUtil;

public class TransporterStore extends ObjectStore<Transporter> {

    TransporterStore(ObjectFactory<Transporter> fct) {
        factory = fct;
    }

    /**
     * print memory usage
     */
    public void printUsage(int indent) {
        BayLog.info("%sTransporterStore Usage:", StringUtil.indent(indent));
        super.printUsage(indent+1);
    }
}
