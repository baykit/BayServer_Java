package yokohama.baykit.bayserver.common;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Rudder {
    Object key();
    void close() throws IOException;

    int read(ByteBuffer buf) throws IOException;
    int write(ByteBuffer buf) throws IOException;
}
