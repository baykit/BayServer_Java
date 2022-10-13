package baykit.bayserver.docker.servlet.duck;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Helper methods for HttpServletResponse
 */
public interface HttpServletResponseHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpServletResponse
    //////////////////////////////////////////////////////////////
    boolean isCommitted(Object res);

    void sendError(Object res, int sc, String msg) throws IOException;

    OutputStream getOutputStreamObject(Object res) throws IOException;

    void setContentLength(Object res, int len);

    void setContentType(Object res, String type);
}
