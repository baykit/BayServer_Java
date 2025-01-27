package yokohama.baykit.bayserver.util;


import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class RoughTime {

    private static volatile long curTime;
    private static final int INTERVAL_MILLISEC = 100;

    // Load class
    public static void init() {
        if(curTime == 0) {
            curTime = System.currentTimeMillis();
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    curTime = System.currentTimeMillis();
                }
            }, 0, INTERVAL_MILLISEC);
        }
    }

    // Get current time
    public static long currentTimeMillis() {
        init();
        return curTime;
    }

    // Get current time as Date
    public static Date currentDate() {
        init();
        return new Date(curTime);
    }
}
