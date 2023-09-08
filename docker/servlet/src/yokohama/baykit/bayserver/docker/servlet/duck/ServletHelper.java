package yokohama.baykit.bayserver.docker.servlet.duck;

import java.io.IOException;

/**
 * Helper methods for Servlet
 */
public interface ServletHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for servlet
    //////////////////////////////////////////////////////////////
    void initServlet(Object servlet, ServletConfigDuck cfg)
            throws ServletExceptionDuck;

    void service(Object servlet, Object req, Object res)
            throws ServletExceptionDuck, IOException;


}
