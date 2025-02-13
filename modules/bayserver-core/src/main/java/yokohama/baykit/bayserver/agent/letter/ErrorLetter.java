package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

public class ErrorLetter  extends Letter{
    public Throwable err;

    public ErrorLetter(int stateId, Rudder rd, Multiplexer mpx, Throwable err) {
        super(stateId, rd, mpx);
        this.err = err;
    }
}
