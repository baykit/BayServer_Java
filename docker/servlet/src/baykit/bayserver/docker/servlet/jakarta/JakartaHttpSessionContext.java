package baykit.bayserver.docker.servlet.jakarta;

import jakarta.servlet.http.*;
import baykit.bayserver.docker.servlet.duck.HttpSessionContextDuck;

import java.util.Enumeration;
import java.util.Vector;

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