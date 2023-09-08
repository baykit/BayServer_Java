package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.duck.HttpSessionDuck;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;

import yokohama.baykit.bayserver.docker.servlet.duck.ServletHelper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/**
 * Implementation of HttpSession for jakarta
 * 
 */
public class JakartaHttpSession extends HttpSessionDuck implements HttpSession {

  
    public JakartaHttpSession(String id, ServletDocker docker, ServletHelper helper) {
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
        return "JakartaSession(" + getId() + ")";
    }
}