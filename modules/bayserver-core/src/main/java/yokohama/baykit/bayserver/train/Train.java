package yokohama.baykit.bayserver.train;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.Counter;

import java.io.IOException;

public abstract class Train implements Runnable {

    protected abstract void depart();

    public final int trainId;
    static Counter trainIdCounter = new Counter();

    public Train() {
        this.trainId = trainIdCounter.next();
    }

    @Override
    public void run() {
        BayLog.debug("%s Start train on: %s", this, Thread.currentThread().getName());
        try {
            depart();
        } catch (Throwable e) {
            BayLog.error(e);
        }
        BayLog.debug("%s End train on: %s", this, Thread.currentThread().getName());
    }
}
