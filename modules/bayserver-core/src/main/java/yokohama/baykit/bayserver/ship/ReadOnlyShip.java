package yokohama.baykit.bayserver.ship;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class ReadOnlyShip extends Ship {

    protected void init(GrandAgent agt) {
        super.init(null, agt, null);
    }

    public abstract NextSocketAction bytesReceived(ByteBuffer buf) throws IOException;
    public abstract NextSocketAction notifyEof();
    public abstract void notifyClose();
    public abstract boolean checkTimeout(int durationSec);


}
