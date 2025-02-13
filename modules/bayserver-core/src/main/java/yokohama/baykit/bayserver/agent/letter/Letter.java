package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public abstract class Letter {
    public final int stateId;
    public final Rudder rudder;
    public final Multiplexer multiplexer;

    public Letter(int stateId, Rudder rd, Multiplexer multiplexer) {
        this.stateId = stateId;
        this.rudder = rd;
        this.multiplexer = multiplexer;
    }
}
