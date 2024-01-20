package yokohama.baykit.bayserver.train;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.VehicleRunner;

public class TrainRunner {

    static VehicleRunner runner = new VehicleRunner();

    //////////////////////////////////////////////
    // Static methods
    //////////////////////////////////////////////
    public static void init(int maxTrains) {
        runner.init(maxTrains);
    }

    /**
     * Run train on available thread
     */
    public static boolean post(int agtId, Train train) {
        BayLog.debug("Agt#%d post train: thread=%s taxi=%s", agtId, Thread.currentThread().getName(), train);
        return runner.post(agtId, train);
    }
}
