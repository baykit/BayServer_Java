package baykit.bayserver.docker.servlet.javax;

import baykit.bayserver.docker.servlet.duck.HttpServletRequestDuck;
import baykit.bayserver.docker.servlet.duck.ServletInputStreamModule;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import java.io.IOException;

class JavaxServletInputStream extends ServletInputStream {

    // like mix-in
    ServletInputStreamModule module;

    JavaxServletInputStream(HttpServletRequestDuck req) {
        this.module = new ServletInputStreamModule(req);
    }

    public int read() throws IOException {
        return module.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return module.read(b, off, len);
    }

    @Override
    public boolean isFinished() {
        return module.isFinished();
    }

    @Override
    public boolean isReady() {
        return module.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        module.setReadListener(readListener);
    }
}