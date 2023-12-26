package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class PacketUnpacker<P extends Packet<?>> implements Reusable {
    
    public abstract NextSocketAction bytesReceived(ByteBuffer bytes) throws IOException;
}
