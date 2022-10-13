package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.ServletOutputStreamModule;
import jakarta.servlet.*;
import baykit.bayserver.docker.servlet.duck.HttpServletResponseDuck;

import java.io.IOException;

class JakartaServletOutputStream extends ServletOutputStream {

    // like mix-in
    private final ServletOutputStreamModule module;

    JakartaServletOutputStream(HttpServletResponseDuck res, String charset){
        this.module = new ServletOutputStreamModule(res, charset);
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////

    @Override
    public boolean isReady() {
        return module.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        module.setWriteListener(writeListener);
    }

    @Override
    public void write(int i) throws IOException {
        module.write(i);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        module.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        module.close();
    }

    @Override
    public void print(String s) throws IOException {
        module.print(s);
    }

    @Override
    public void flush() throws IOException {
        module.flush();
    }
}