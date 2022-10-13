package baykit.bayserver.docker.servlet.duck;

import java.io.IOException;
import java.io.InputStream;

public class ServletInputStreamModule {

    InputStream in;
    int pos;
    Object readListener;

    public ServletInputStreamModule(HttpServletRequestDuck req) {
        this.in = req.in;
    }

    public synchronized int read() throws IOException {
        byte b[] = new byte[1];
        int c = read(b, 0, b.length);
        //System.err.println("read:" + new String(b));
        if(c <= 0)
            return -1;
        else
            return (int)b[0] & 0xFF;
    }

    public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (len == 0)
            return 0;

        return in.read(b, off, len);
    }

    public boolean isFinished() {
        return false;
    }

    public boolean isReady() {
        return false;
    }

    public void setReadListener(Object readListener) {
        this.readListener = readListener;
    }
}
