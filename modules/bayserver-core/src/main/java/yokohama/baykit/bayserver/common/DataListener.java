package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * This interface is for delivering received data to InboundShip
 */
public interface DataListener {

    NextSocketAction notifyConnect() throws IOException;

    NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException;

    NextSocketAction notifyEof();

    NextSocketAction notifyHandshakeDone(String protocol) throws IOException;

    boolean notifyProtocolError(ProtocolException e) throws IOException;

    void notifyError(Throwable e);

    void notifyClose();

    boolean checkTimeout(int durationSec);

}
