package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class ConnectedLetter extends Letter {

    public ConnectedLetter(Rudder rd, Multiplexer mpx) {
        super(rd, mpx);
    }
}
