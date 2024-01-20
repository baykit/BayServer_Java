package yokohama.baykit.bayserver.common;

import java.io.IOException;

public interface Rudder {
    Object key();
    void close() throws IOException;
}
