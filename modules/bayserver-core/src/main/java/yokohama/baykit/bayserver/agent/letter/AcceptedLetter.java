package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class AcceptedLetter extends Letter {
    public Rudder clientRudder;

    public AcceptedLetter(int stateId, Rudder rd, Multiplexer mpx, Rudder clientRd) {
        super(stateId, rd, mpx);
        this.clientRudder = clientRd;
    }
}
