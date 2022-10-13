package baykit.bayserver.docker.servlet.javax;

import javax.servlet.http.*;
import baykit.bayserver.docker.servlet.duck.HttpSessionContextDuck;

import java.util.Enumeration;
import java.util.Vector;

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