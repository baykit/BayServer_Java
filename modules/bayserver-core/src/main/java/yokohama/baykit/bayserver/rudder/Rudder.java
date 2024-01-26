package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Rudder {
    Object key();

    void setNonBlocking() throws IOException;

    int read(ByteBuffer buf) throws IOException;
    int write(ByteBuffer buf) throws IOException;

    void close() throws IOException;
}
