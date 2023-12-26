package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * This interface absorbs the transport layer such as TCP/IP, UDP
 */
public interface DataListener {

    NextSocketAction notifyConnect() throws IOException;

    NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException;

    NextSocketAction notifyEof();

    NextSocketAction notifyHandshakeDone(String protocol) throws IOException;

    boolean notifyProtocolError(ProtocolException e) throws IOException;

    void notifyClose();

    boolean checkTimeout(int durationSec);
}
