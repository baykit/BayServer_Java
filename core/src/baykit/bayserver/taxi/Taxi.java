package baykit.bayserver.taxi;

import baykit.bayserver.BayLog;
import baykit.bayserver.util.Counter;

public abstract class Taxi implements Runnable {

    static Counter taxiIdCounter = new Counter();

    final int taxiId;

    public Taxi() {
        this.taxiId = taxiIdCounter.next();
    }

    @Override
    public String toString() {
        return "taxi#" + taxiId;
    }

    @Override
    public void run() {
        BayLog.trace("%s Start taxi on: %s", this, Thread.currentThread().getName());
        depart();
        BayLog.trace("%s End taxi on: %s", this, Thread.currentThread().getName());
    }

    protected abstract void depart();
}
