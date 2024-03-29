package yokohama.baykit.bayserver.train;

import yokohama.baykit.bayserver.BayLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class TrainRunner {

    static ExecutorService exe;

    public static void init(int count) {
        if(count <= 0)
            throw new IllegalArgumentException();
        exe = Executors.newFixedThreadPool(count);
    }

    /**
     * Run train on available thread
     */
    public static boolean post(Train train) {
        try {
            exe.submit(train);
            return true;
        } catch(RejectedExecutionException e) {
            BayLog.error(e);
            return false;
        }
    }
}
