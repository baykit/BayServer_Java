package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class WriteUnit {
    public final ByteBuffer buf;
    public final InetSocketAddress adr;
    public final Object tag;
    public final DataConsumeListener listener;

    WriteUnit(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
        this.buf = buf;
        this.adr = adr;
        this.tag = tag;
        this.listener = listener;
    }

    public void done() {
        if (listener != null)
            listener.dataConsumed();
    }
}
