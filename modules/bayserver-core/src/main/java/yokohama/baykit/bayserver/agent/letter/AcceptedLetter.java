package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.rudder.Rudder;

public class AcceptedLetter extends Letter {
    public Rudder clientRudder;

    public AcceptedLetter(RudderState st, Rudder clientRd) {
        super(st);
        this.clientRudder = clientRd;
    }
}
