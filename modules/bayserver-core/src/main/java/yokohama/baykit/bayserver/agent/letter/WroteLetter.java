package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.RudderState;

public class WroteLetter extends Letter {
    public int nBytes;

    public WroteLetter(RudderState st, int n) {
        super(st);
        this.nBytes = n;
    }
}
