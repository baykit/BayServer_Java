package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.HttpServletResponseHelper;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

public class JavaxHttpServletResponseHelper implements HttpServletResponseHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpServletResponse
    //////////////////////////////////////////////////////////////

    @Override
    public boolean isCommitted(Object res) {
        return ((ServletResponse)res).isCommitted();
    }

    @Override
    public void sendError(Object res, int sc, String msg) throws IOException {
        ((HttpServletResponse)res).sendError(sc, msg);
    }

    @Override
    public OutputStream getOutputStreamObject(Object res) throws IOException {
        return ((ServletResponse)res).getOutputStream();
    }

    @Override
    public void setContentLength(Object res, int len) {
        ((ServletResponse)res).setContentLength(len);
    }

    @Override
    public void setContentType(Object res, String type) {
        ((ServletResponse)res).setContentType(type);
    }
}
