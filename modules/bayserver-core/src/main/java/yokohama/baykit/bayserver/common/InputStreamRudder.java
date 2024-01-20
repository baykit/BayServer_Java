package yokohama.baykit.bayserver.common;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamRudder implements Rudder{
    public final InputStream input;

    public InputStreamRudder(InputStream in) {
        this.input = in;
    }

    @Override
    public String toString() {
        return input.toString();
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public Object key() {
        return input;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
