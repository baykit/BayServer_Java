package baykit.bayserver.protocol;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class PacketUnpacker<P extends Packet<?>> implements Reusable {
    
    public abstract NextSocketAction bytesReceived(ByteBuffer bytes) throws IOException;
}
