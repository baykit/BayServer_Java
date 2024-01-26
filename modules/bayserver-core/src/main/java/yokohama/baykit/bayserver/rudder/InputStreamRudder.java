package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class InputStreamRudder implements Rudder {
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
    public void setNonBlocking() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        return input.read(buf.array(), buf.position(), buf.limit() - buf.position());
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        throw new IOException("Cannot write to InputStream");
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    ////////////////////////////////////////////
    // Static functions
    ////////////////////////////////////////////

    public static InputStream getInputStream(Rudder rd) {
        return ((InputStreamRudder)rd).input;
    }
}
