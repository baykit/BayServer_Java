package yokohama.baykit.bayserver.rudder;

import java.io.IOException;

public abstract  class RudderBase implements Rudder {
    boolean closed;

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public final boolean closed() {
        return closed;
    }
}
