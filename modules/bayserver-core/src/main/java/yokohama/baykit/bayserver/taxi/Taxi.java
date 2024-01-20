package yokohama.baykit.bayserver.taxi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.Vehicle;
import yokohama.baykit.bayserver.util.Counter;

public abstract class Taxi extends Vehicle {

    static Counter taxiIdCounter = new Counter();

    public Taxi() {
        super(taxiIdCounter.next());
    }

    @Override
    public String toString() {
        return "taxi#" + id;
    }

    public void run() {
        BayLog.trace("%s Start taxi on: %s", this, Thread.currentThread().getName());
        try {
            depart();
        }
        catch(Throwable e) {
            BayLog.error(e);
        }
        finally {
            BayLog.trace("%s End taxi on: %s", this, Thread.currentThread().getName());
        }
    }

    protected abstract void depart();
}
