package yokohama.baykit.bayserver.util;


import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A class for retrieving time with low CPU overhead as an alternative to System.currentTimeMillis.
 */
public class RoughTime {

    private static volatile long curTime = System.currentTimeMillis();
    private static final int INTERVAL_MILLISEC = 100;

    static {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                curTime = System.currentTimeMillis();
            }
        }, 0, INTERVAL_MILLISEC);
    }

    // Load class
    public static void init() {
    }

    // Get current time
    public static long currentTimeMillis() {
        return curTime;
    }

    // Get current time as Date
    public static Date currentDate() {
        return new Date(curTime);
    }
}