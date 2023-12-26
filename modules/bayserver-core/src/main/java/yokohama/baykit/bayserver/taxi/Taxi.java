package yokohama.baykit.bayserver.taxi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.util.Counter;

public abstract class Taxi {

    static Counter taxiIdCounter = new Counter();

    protected final int taxiId;

    public Taxi() {
        this.taxiId = taxiIdCounter.next();
    }

    @Override
    public String toString() {
        return "taxi#" + taxiId;
    }

    public void run() {
        BayLog.trace("%s Start taxi on: %s", this, Thread.currentThread().getName());
        depart();
        BayLog.trace("%s End taxi on: %s", this, Thread.currentThread().getName());
    }

    protected abstract void depart();
    protected abstract void onTimer();
}
