package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public interface Transporter extends Reusable {

    void init();

    NextSocketAction onConnect(Rudder rd) throws IOException;

    NextSocketAction onRead(Rudder rd, ByteBuffer data, InetSocketAddress adr) throws IOException;

    void onError(Rudder rd, Throwable e);

    void onClosed(Rudder rd);

    void reqConnect(Rudder rd, SocketAddress addr) throws IOException;

    void reqRead(Rudder rd);

    void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException;

    void reqClose(Rudder rd);

    boolean checkTimeout(Rudder rd, int durationSec);

    int getReadBufferSize();

    /**
     * print memory usage
     */
    void printUsage(int indent);
}
