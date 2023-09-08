package yokohama.baykit.bayserver.docker.servlet.javax;

import javax.servlet.http.*;
import yokohama.baykit.bayserver.docker.servlet.duck.HttpSessionContextDuck;

/**
 * Implementation of HttpSessionContext for javax
 * 
 */
public class JavaxHttpSessionContext extends HttpSessionContextDuck implements HttpSessionContext {


    /**
     * Depreceted Return null
     */
    @Override
    public HttpSession getSession(String sessionId) {
        return null;
    }
}