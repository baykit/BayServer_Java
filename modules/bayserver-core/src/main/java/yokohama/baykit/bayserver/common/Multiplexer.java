package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.rudder.Rudder;
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

    void addRudderState(Rudder rd, RudderState st);

    RudderState getRudderState(Rudder rd);

    Transporter getTransporter(Rudder rd);

    void reqConnect(Rudder rd, SocketAddress addr) throws IOException;

    void reqRead(Rudder rd);

    void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException;

    void reqEnd(Rudder rd);

    void reqClose(Rudder rd);

    void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh);

    void cancelRead(RudderState st);

    void cancelWrite(RudderState st);

    void nextAccept(RudderState state);
    void nextRead(RudderState st);
    void nextWrite(RudderState st);

    void shutdown();

    boolean isBusy();

    boolean useAsyncAPI();

    boolean consumeOldestUnit(RudderState st);
    void closeRudder(RudderState st);

    void onBusy();

    void onFree();
}
