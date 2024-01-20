package yokohama.baykit.bayserver.train;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.Vehicle;
import yokohama.baykit.bayserver.util.Counter;

public abstract class Train extends Vehicle {

    protected abstract void depart();

    static Counter trainIdCounter = new Counter();

    public Train() {
        super(trainIdCounter.next());
    }

    @Override
    public String toString() {
        return "train#" + id;
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
