package yokohama.baykit.bayserver.docker.servlet.duck;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;

import java.io.IOException;

public class ServletOutputStreamModule {

    /** Connection */
    private final HttpServletResponseDuck res;

    String charset;

    public ServletOutputStreamModule(HttpServletResponseDuck res, String charset){
        this.res = res;
        this.charset = charset == null ? ServletDocker.DEFAULT_CHARSET : charset;
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////

    public boolean isReady() {
        return true;
    }

    public void setWriteListener(Object writeListener) {

    }

    public void write(int i) throws IOException {
        write(new byte[]{(byte)i}, 0, 1);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        sendHeaders();
        res.sendContent(b, off, len);
    }

    public void close() throws IOException {
        sendHeaders();
    }

    public void print(String s) throws IOException {
        if (s == null)
            s = "null";

        byte[] b = s.getBytes(charset);
        write(b, 0, b.length);
    }

    public void flush() throws IOException {
        sendHeaders();
    }

    //////////////////////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////////////////////
    protected void sendHeaders() throws IOException {
        if (!res.tour.res.headerSent()) {
            res.sendHeaders();
        }
    }
}
