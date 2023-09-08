package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.servlet.ReqInfo;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class JavaxDuckFactory implements DuckFactory {

    ServletDocker docker;

    public JavaxDuckFactory(ServletDocker docker) {
        this.docker = docker;
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet context
    //////////////////////////////////////////////////////////////

    @Override
    public ServletContextDuck newContext(String name, String ctxPath, String realPath, ServletDocker docker) {
        return new JavaxServletContext(name, ctxPath, realPath, docker);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for session
    //////////////////////////////////////////////////////////////

    @Override
    public HttpSessionDuck newSession(String id, ServletDocker docker) {
        return new JavaxHttpSession(id, docker, docker.servletHelper);
    }

    @Override
    public HttpSessionContextDuck newSessionContext() {
        return new JavaxHttpSessionContext();
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet
    //////////////////////////////////////////////////////////////

    @Override
    public ServletConfigDuck newServletConfig(ServletContextDuck ctx, Map<String, String> params, String name) {
        return new JavaxServletConfig(ctx, params, name);
    }

    @Override
    public ServletRegistrationDuck newServletRegistration(String name, String className) {
        return new JavaxServletRegistration(name, className, docker.ctx);
    }

    @Override
    public ServletRegistrationDuck newServletRegistration(String name, Object servlet) {
        return new JavaxServletRegistration(name, servlet, docker.ctx);
    }

    @Override
    public Object newFileServlet() {
        return new JavaxFileServlet();
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet request
    //////////////////////////////////////////////////////////////

    @Override
    public HttpServletRequestDuck newRequest(
            Tour tour,
            ReqInfo reqInfo,
            HttpServletResponseDuck res,
            ServletDocker docker,
            boolean async) {
        return new JavaxHttpServletRequest(tour, reqInfo, res, docker, async);
    }

    @Override
    public InputStream newServletInputStream(HttpServletRequestDuck req) {
        return new JavaxServletInputStream(req);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet response
    //////////////////////////////////////////////////////////////

    @Override
    public HttpServletResponseDuck newResponse(Tour tour, ServletDocker docker) {
        return new JavaxHttpServletResponse(tour, docker);
    }

    @Override
    public OutputStream newServletOutputStream(HttpServletResponseDuck res, String charset) {
        return new JavaxServletOutputStream(res, charset);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for filter
    //////////////////////////////////////////////////////////////
    @Override
    public FilterConfigDuck newFilterConfig(FilterRegistrationDuck reg, ServletContextDuck ctx) {
        return new JavaxFilterConfig(ctx, reg);
    }

    @Override
    public FilterRegistrationDuck newFilterRegistration(String name, String className) {
        return new JavaxFilterRegistration(name, className, docker.ctx);
    }

    @Override
    public FilterRegistrationDuck newFilterRegistration(String name, Object filter) {
        return new JavaxFilterRegistration(name, filter, docker.ctx);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for dispatcher
    //////////////////////////////////////////////////////////////

    @Override
    public RequestDispatcherDuck newNamedDispatcher(Object servlet) {
        return new JavaxNamedDispatcher(servlet, docker, docker.ctx.getContextPath());
    }

    @Override
    public RequestDispatcherDuck newRequestDispatcher(
            Object servlet,
            ReqInfo reqInfo,
            String reqUri,
            String servletPath,
            String pathInfo,
            String qstr) {
        return new JavaxRequestDispatcher(
                servlet,
                docker,
                reqInfo,
                docker.ctx.getContextPath(),
                reqUri,
                servletPath,
                pathInfo,
                qstr);
    }

    @Override
    public Object newForwardRequest(
            Object req,
            String reqUri,
            String servletPath,
            String pathInfo,
            String queryString) {
        return new JavaxForwardRequest(
                (HttpServletRequest)req,
                docker.ctx,
                reqUri,
                servletPath,
                pathInfo,
                queryString);
    }

    @Override
    public Object newIncluderequest(Object req, String queryString) {
        return new JavaxIncludeRequest((HttpServletRequest)req, queryString);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for ASyncContext
    //////////////////////////////////////////////////////////////


    @Override
    public ASyncContextDuck newAsyncContext(Object req, Object res, boolean original, ServletDocker docker) {
        return new JavaxAsyncContext(
                req,
                res,
                original,
                docker);
    }

    //////////////////////////////////////////////////////////////
    // Factory functions for Listener events
    //////////////////////////////////////////////////////////////

    @Override
    public Object newServletContextEvent(ServletContextDuck ctx) {
        return new ServletContextEvent((ServletContext)ctx);
    }

    @Override
    public Object newContextAttributeEvent(ServletContextDuck ctx, String name, Object value) {
        return new ServletContextAttributeEvent((ServletContext) ctx, name, value);
    }

    @Override
    public Object newHttpSessionEvent(HttpSessionDuck session) {
        return new HttpSessionEvent((HttpSession) session);
    }

    @Override
    public Object newSessionBindingEvent(HttpSessionDuck session, String name, Object value) {
        return new HttpSessionBindingEvent((HttpSession)session, name, value);
    }

    @Override
    public Object newServletRequestEvent(ServletContextDuck ctx, Object req) {
        return new ServletRequestEvent((ServletContext) ctx, (ServletRequest) req);
    }

    @Override
    public Object newServletRequestAttributeEvent(ServletContextDuck ctx, Object req, String name, Object value) {
        return new ServletRequestAttributeEvent((ServletContext) ctx, (ServletRequest) req, name, value);
    }

    @Override
    public Object newAsyncEvent(ASyncContextDuck ctx) {
        return new AsyncEvent((AsyncContext) ctx);
    }

    @Override
    public Object newAsyncEvent(ASyncContextDuck ctx, Object req, Object res) {
        return new AsyncEvent((AsyncContext) ctx, (ServletRequest) req, (ServletResponse) res);
    }
}
