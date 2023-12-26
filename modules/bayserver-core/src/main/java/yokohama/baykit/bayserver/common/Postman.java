package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface Postman extends Reusable {
    void post(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException;

    void flush();

    void postEnd();

    boolean isZombie();

    void abort();
}
