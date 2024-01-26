package yokohama.baykit.bayserver.common;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamRudder implements Rudder{
    public final OutputStream output;

    public OutputStreamRudder(OutputStream out) {
        this.output = out;
    }

    @Override
    public String toString() {
        return output.toString();
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public Object key() {
        return output;
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    ////////////////////////////////////////////
    // Static functions
    ////////////////////////////////////////////

    public static OutputStream getOutputStream(Rudder rd) {
        return ((OutputStreamRudder)rd).output;
    }
}
