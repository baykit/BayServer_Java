package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class ConnectedLetter extends Letter {

    public ConnectedLetter(int stateId, Rudder rd, Multiplexer mpx) {
        super(stateId, rd, mpx);
    }
}
