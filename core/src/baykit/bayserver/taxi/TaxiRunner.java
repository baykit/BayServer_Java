package baykit.bayserver.taxi;

import baykit.bayserver.BayLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class TaxiRunner {

    static ExecutorService exe;

    public static void init(int count) {
        if(count <= 0)
            throw new IllegalArgumentException();
        exe = Executors.newFixedThreadPool(count);
    }

    public static boolean post(Taxi txi) {
        try {
            exe.submit(txi);
            return true;
        } catch(RejectedExecutionException e) {
            BayLog.error(e);
            return false;
        }
    }
}
