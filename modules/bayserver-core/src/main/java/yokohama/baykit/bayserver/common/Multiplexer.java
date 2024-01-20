package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.RudderState;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

/**
 * Managements I/O Multiplexing
 *  (Possible implementations include the select system call, event APIs, or threading)
 */
public interface Multiplexer {

    void addState(Rudder rd, RudderState st);

    void reqStart(Rudder rd);

    void reqConnect(Rudder rd, SocketAddress addr) throws IOException;

    void reqRead(Rudder rd);

    void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException;

    void reqEnd(Rudder rd);

    void reqClose(Rudder rd);

    void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh);

    void shutdown();
}
