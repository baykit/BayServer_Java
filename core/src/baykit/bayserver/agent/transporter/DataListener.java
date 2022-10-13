package baykit.bayserver.agent.transporter;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface DataListener {

    NextSocketAction notifyConnect() throws IOException;

    NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException;

    NextSocketAction notifyEof() throws IOException;

    NextSocketAction notifyHandshakeDone(String protocol) throws IOException;

    boolean notifyProtocolError(ProtocolException e) throws IOException;

    void notifyClose();

    boolean checkTimeout(int durationSec);
}
