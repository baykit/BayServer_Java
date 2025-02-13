package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class ClosedLetter extends Letter{

    public ClosedLetter(int stateId, Rudder rd, Multiplexer mpx) {
        super(stateId, rd, mpx);
    }
}
