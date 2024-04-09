package yokohama.baykit.bayserver.taxi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.VehicleRunner;

public class TaxiRunner {

    static VehicleRunner runner = new VehicleRunner();

    //////////////////////////////////////////////
    // Static methods
    //////////////////////////////////////////////
    public static void init(int maxTaxis) {
        runner.init(maxTaxis);
    }

    public static boolean post(int agtId, Taxi txi) {
        BayLog.debug("agt#%d(%s) post taxi: taxi=%s", agtId, Thread.currentThread().getName(), txi);
        return runner.post(agtId, txi);
    }
}
