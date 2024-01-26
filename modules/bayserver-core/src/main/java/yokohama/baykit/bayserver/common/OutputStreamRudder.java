package yokohama.baykit.bayserver.common;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

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
    public int read(ByteBuffer buf) throws IOException {
        throw new IOException("Cannot read from OutputStream");
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        int len = buf.limit() - buf.position();
        output.write(buf.array(), buf.position(), len);
        return len;
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
