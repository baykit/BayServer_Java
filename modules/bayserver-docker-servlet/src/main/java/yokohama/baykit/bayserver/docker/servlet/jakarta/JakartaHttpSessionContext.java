package yokohama.baykit.bayserver.docker.servlet.jakarta;

import jakarta.servlet.http.*;
import yokohama.baykit.bayserver.docker.servlet.duck.HttpSessionContextDuck;

/**
 * Implementation of HttpSessionContext for jakarta
 * 
 */
public class JakartaHttpSessionContext extends HttpSessionContextDuck implements HttpSessionContext {


    /**
     * Depreceted Return null
     */
    @Override
    public HttpSession getSession(String sessionId) {
        return null;
    }
}