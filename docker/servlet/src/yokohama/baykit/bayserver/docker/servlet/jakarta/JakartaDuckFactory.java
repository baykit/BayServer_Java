package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.servlet.ReqInfo;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class JakartaDuckFactory implements DuckFactory {

    ServletDocker docker;

    public JakartaDuckFactory(ServletDocker docker) {
        this.docker = docker;
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet context
    //////////////////////////////////////////////////////////////

    @Override
    public ServletContextDuck newContext(String name, String ctxPath, String realPath, ServletDocker docker) {
        return new JakartaServletContext(name, ctxPath, realPath, docker);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for session
    //////////////////////////////////////////////////////////////

    @Override
    public HttpSessionDuck newSession(String id, ServletDocker docker) {
        return new JakartaHttpSession(id, docker, docker.servletHelper);
    }

    @Override
    public HttpSessionContextDuck newSessionContext() {
        return new JakartaHttpSessionContext();
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet
    //////////////////////////////////////////////////////////////

    @Override
    public ServletConfigDuck newServletConfig(ServletContextDuck ctx, Map<String, String> params, String name) {
        return new JakartaServletConfig(ctx, params, name);
    }

    @Override
    public ServletRegistrationDuck newServletRegistration(String name, String className) {
        return new JakartaServletRegistration(name, className, docker.ctx);
    }

    @Override
    public ServletRegistrationDuck newServletRegistration(String name, Object servlet) {
        return new JakartaServletRegistration(name, servlet, docker.ctx);
    }

    @Override
    public Object newFileServlet() {
        return new JakartaFileServlet();
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
        return new JakartaHttpServletRequest(tour, reqInfo, res, docker, async);
    }

    @Override
    public InputStream newServletInputStream(HttpServletRequestDuck req) {
        return new JakartaServletInputStream(req);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet response
    //////////////////////////////////////////////////////////////

    @Override
    public HttpServletResponseDuck newResponse(Tour tour, ServletDocker docker) {
        return new JakartaHttpServletResponse(tour, docker);
    }

    @Override
    public OutputStream newServletOutputStream(HttpServletResponseDuck res, String charset) {
        return new JakartaServletOutputStream(res, charset);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for filter
    //////////////////////////////////////////////////////////////
    @Override
    public FilterConfigDuck newFilterConfig(FilterRegistrationDuck reg, ServletContextDuck ctx) {
        return new JakartaFilterConfig(ctx, reg);
    }

    @Override
    public FilterRegistrationDuck newFilterRegistration(String name, String className) {
        return new JakartaFilterRegistration(name, className, docker.ctx);
    }

    @Override
    public FilterRegistrationDuck newFilterRegistration(String name, Object filter) {
        return new JakartaFilterRegistration(name, filter, docker.ctx);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for dispatcher
    //////////////////////////////////////////////////////////////

    @Override
    public RequestDispatcherDuck newNamedDispatcher(Object servlet) {
        return new JakartaNamedDispatcher(servlet, docker, docker.ctx.getContextPath());
    }

    @Override
    public RequestDispatcherDuck newRequestDispatcher(
            Object servlet,
            ReqInfo reqInfo,
            String reqUri,
            String servletPath,
            String pathInfo,
            String qstr) {
        return new JakartaRequestDispatcher(
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
        return new JakartaForwardRequest(
                (HttpServletRequest)req,
                docker.ctx,
                reqUri,
                servletPath,
                pathInfo,
                queryString);
    }

    @Override
    public Object newIncluderequest(Object req, String queryString) {
        return new JakartaIncludeRequest((HttpServletRequest)req, queryString);
    }

    //////////////////////////////////////////////////////////////
    // Factory methods for ASyncContext
    //////////////////////////////////////////////////////////////


    @Override
    public ASyncContextDuck newAsyncContext(Object req, Object res, boolean original, ServletDocker docker) {
        return new JakartaAsyncContext(
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
