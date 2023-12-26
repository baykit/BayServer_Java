package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.ship.Ship;

public abstract class WriteOnlyShip extends Ship {

    protected void init(GrandAgent agt) {
        super.init(null, agt, null);
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract void notifyClose();
}
