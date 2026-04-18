package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * Managements I/O Multiplexing
 *  (Possible implementations include the select system call, event APIs, or threading)
 */
public interface Multiplexer {

    void addRudderState(Rudder rd, RudderState st);

    void removeRudderState(Rudder rd);

    RudderState getRudderState(Rudder rd);

    Transporter getTransporter(Rudder rd);

    void reqAccept(Rudder rd);

    void reqConnect(Rudder rd, SocketAddress addr) throws IOException;

    void reqRead(Rudder rd);

    /**
     * Request to write data to the rudder.
     *
     * Returns whether the internal write buffer still has room. The buffer
     * capacity is the shipBufferSize parameter (configured on the Harbor docker);
     * the return value is true when the pending data in the write queue is
     * less than or equal to shipBufferSize, and false once it exceeds that
     * threshold. When false is returned, the caller should stop submitting
     * further writes and wait until the internal buffer has room again before
     * resuming.
     */
    boolean reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, boolean flush, DataConsumeListener listener) throws IOException;

    void reqTransfer(Rudder rd, Rudder fileRd, int ofs, int len, DataConsumeListener listener) throws IOException;

    void reqEnd(Rudder rd);

    void reqClose(Rudder rd);

    void cancelRead(RudderState st);

    void cancelWrite(RudderState st);

    void nextAccept(RudderState state);
    void nextRead(RudderState st);
    void nextWrite(RudderState st);

    void shutdown();

    boolean isNonBlocking();
    boolean useAsyncAPI();

    boolean consumeOldestUnit(RudderState st);
    void closeRudder(Rudder rd);

    boolean isBusy();
    void onBusy();
    void onFree();
}
