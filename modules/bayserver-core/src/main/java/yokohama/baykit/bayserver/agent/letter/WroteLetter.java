package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class WroteLetter extends Letter {
    public int nBytes;

    public WroteLetter(Rudder rd, Multiplexer mpx, int n) {
        super(rd, mpx);
        this.nBytes = n;
    }
}
