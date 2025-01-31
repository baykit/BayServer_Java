package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public abstract class Letter {
    public Rudder rudder;
    public Multiplexer multiplexer;

    public Letter(Rudder rd, Multiplexer multiplexer) {
        this.rudder = rd;
        this.multiplexer = multiplexer;
    }
}
