package baykit.bayserver.docker.servlet.javax;

import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.servlet.ReqInfo;
import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.HttpServletRequestDuck;
import baykit.bayserver.docker.servlet.duck.HttpServletResponseDuck;
import baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import baykit.bayserver.docker.servlet.javax.JavaxHttpServletResponse;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

class JavaxHttpServletRequest extends HttpServletRequestDuck implements HttpServletRequest {

    /**
     * Constructor
     */
    public JavaxHttpServletRequest(
            Tour tour,
            ReqInfo reqInfo,
            HttpServletResponseDuck res,
            ServletDocker docker,
            boolean async) {
        super(tour, reqInfo, res, docker, async);
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////

    @Override
    public Cookie[] getCookies() {
        return (Cookie[])reqInfo.cookies;
    }

    @Override
    public HttpSession getSession(boolean create) {
        return (HttpSession)getSessionDuck();
    }

    @Override
    public HttpSession getSession() {
        return (HttpSession)getSessionDuck(true);
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        try {
            Collection<Object> oparts = getPartObjects();
            return oparts.stream().map( part -> (Part)part ).collect(Collectors.toList());
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        try {
            return (Part)getPartObject(name);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
        try {
            return upgradeInDuck(aClass);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }
     
    @Override
    public ServletInputStream getInputStream() throws IOException {
        return (ServletInputStream)getInputStreamObject();
    }


    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return (RequestDispatcher)getRequestDispatcherDuck(path);
    }

    @Override
    public ServletContext getServletContext() {
        return (ServletContext)docker.ctx;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return (AsyncContext)startAsyncDuck();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        return (AsyncContext)startAsyncDuck((HttpServletRequestDuck)servletRequest, (JavaxHttpServletResponse)servletResponse);
    }

    @Override
    public AsyncContext getAsyncContext() {
        return (AsyncContext)getAsyncContextDuck();
    }

    @Override
    public DispatcherType getDispatcherType() {
        return (DispatcherType)getDispatcherTypeObject();
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
        try {
            return authenticateDuck((JavaxHttpServletResponse)httpServletResponse);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public void login(String username, String password) throws ServletException {
        try {
            loginDuck(username, password);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public void logout() throws ServletException {
        try {
            logoutDuck();
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }
}