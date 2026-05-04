package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WriteUnit {
    public final ByteBuffer buf;
    public final Rudder file;
    public final InetSocketAddress adr;
    public final Object tag;
    public final DataConsumeListener listener;
    public int written;
    public final int offset;
    public final int length;
    /** Total bytes this unit will eventually write. Captured at construction
     *  so RudderState.writeQueueBytes can be incremented on add and
     *  decremented on full consume in O(1). For ByteBuffer mode it's the
     *  initial buf.remaining(); for sendfile mode it's `length`. */
    public final int initialSize;

    public WriteUnit(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
        this(buf, null, 0, 0, adr, tag, listener);
    }

    public WriteUnit(Rudder file, int ofs, int len, DataConsumeListener listener) {
        this(null, file, ofs, len, null, null, listener);
    }

    private WriteUnit(ByteBuffer buf, Rudder file, int ofs, int len, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
        this.buf = buf;
        this.file = file;
        this.offset = ofs;
        this.length = len;
        this.adr = adr;
        this.tag = tag;
        this.listener = listener;
        this.initialSize = (buf != null) ? buf.remaining() : len;
    }

    public void done(boolean bufferAvailable) {
        if (listener != null)
            listener.dataConsumed(bufferAvailable);
    }

    public boolean skipFormalities() {
        return file != null;
    }

    public int position() {
        if(skipFormalities())
            return offset + written;
        else
            return buf.position();
    }

    public int remaining() {
        if(skipFormalities())
            return length - written;
        else
            return buf.remaining();
    }

    public void forward(int len) {
        written += len;
    }

    public boolean hasRemaining() {
        if(skipFormalities())
            return length > written;
        else
            return buf.hasRemaining();
    }
}
