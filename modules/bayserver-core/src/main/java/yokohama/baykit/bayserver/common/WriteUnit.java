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
    }

    public void done() {
        if (listener != null)
            listener.dataConsumed();
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
