package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.RudderState;

public abstract class Letter {
    public RudderState state;

    public Letter(RudderState st) {
        this.state = st;
    }
}
