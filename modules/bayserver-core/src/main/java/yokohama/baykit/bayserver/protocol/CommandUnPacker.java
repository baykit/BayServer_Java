package yokohama.baykit.bayserver.protocol;


import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;

public abstract class CommandUnPacker<P extends Packet<?>> implements Reusable {

    public abstract NextSocketAction packetReceived(P pac) throws IOException;
}
