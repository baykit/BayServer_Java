package yokohama.baykit.bayserver.ship;

import yokohama.baykit.bayserver.agent.GrandAgent;

public abstract class WriteOnlyShip extends Ship {

    protected void init(GrandAgent agt) {
        super.init(null, agt, null);
    }

    public abstract void notifyClose();
}
