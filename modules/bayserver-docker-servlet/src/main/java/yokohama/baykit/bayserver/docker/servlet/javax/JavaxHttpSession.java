package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.HttpSessionDuck;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;

import yokohama.baykit.bayserver.docker.servlet.duck.ServletHelper;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * Implementation of HttpSession for javax
 * 
 */
public class JavaxHttpSession extends HttpSessionDuck implements HttpSession {

  
    public JavaxHttpSession(String id, ServletDocker docker, ServletHelper helper) {
        super(id, docker, helper);
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////
    @Override
    public ServletContext getServletContext() {
        return (ServletContext)super.getServletContextDuck();
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return (HttpSessionContext)super.getSessionContextDuck();
    }

    //////////////////////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////////////////////

    public String toString() {
        return "JavaxSession(" + getId() + ")";
    }
}