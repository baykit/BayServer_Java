package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.agent.multiplexer.RudderState;

public class ErrorLetter  extends Letter{
    public Throwable err;

    public ErrorLetter(RudderState st, Throwable err) {
        super(st);
        this.err = err;
    }
}
