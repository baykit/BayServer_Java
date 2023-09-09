package yokohama.baykit.bayserver.docker.servlet.duck;

import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.servlet.ReqInfo;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public interface DuckFactory {

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet context
    //////////////////////////////////////////////////////////////
    ServletContextDuck newContext(String name, String ctxPath, String realPath, ServletDocker docker);

    //////////////////////////////////////////////////////////////
    // Factory methods for session
    //////////////////////////////////////////////////////////////

    HttpSessionDuck newSession(String id, ServletDocker docker);

    HttpSessionContextDuck newSessionContext();

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet
    //////////////////////////////////////////////////////////////
    ServletConfigDuck newServletConfig(ServletContextDuck ctx, Map<String, String> params, String name);

    ServletRegistrationDuck newServletRegistration(String name, String className);

    ServletRegistrationDuck newServletRegistration(String name, Object servlet);

    Object newFileServlet();

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet request
    //////////////////////////////////////////////////////////////

    HttpServletRequestDuck newRequest(
            Tour tour,
            ReqInfo reqInfo,
            HttpServletResponseDuck res,
            ServletDocker docker,
            boolean async);

    InputStream newServletInputStream(HttpServletRequestDuck req);

    //////////////////////////////////////////////////////////////
    // Factory methods for servlet response
    //////////////////////////////////////////////////////////////

    HttpServletResponseDuck newResponse(Tour tour, ServletDocker docker);

    OutputStream newServletOutputStream(HttpServletResponseDuck res, String charset);

    //////////////////////////////////////////////////////////////
    // Helper methods for filter
    //////////////////////////////////////////////////////////////

    FilterConfigDuck newFilterConfig(FilterRegistrationDuck reg, ServletContextDuck ctx);

    FilterRegistrationDuck newFilterRegistration(String name, String className);

    FilterRegistrationDuck newFilterRegistration(String name, Object filter);

    //////////////////////////////////////////////////////////////
    // Factory methods for dispatcher
    //////////////////////////////////////////////////////////////
    RequestDispatcherDuck newNamedDispatcher(Object svt);

    RequestDispatcherDuck newRequestDispatcher(
            Object matchedObj,
            ReqInfo reqInfo,
            String reqUri,
            String servletPath,
            String pathInfo,
            String qstr);

    Object newForwardRequest(
            Object req,
            String reqUri,
            String servletPath,
            String pathInfo,
            String queryString);

    Object newIncluderequest(Object req, String queryString);

    //////////////////////////////////////////////////////////////
    // Factory methods for ASyncContext
    //////////////////////////////////////////////////////////////
    ASyncContextDuck newAsyncContext(
            Object req,
            Object res,
            boolean original,
            ServletDocker docker);

    //////////////////////////////////////////////////////////////
    // Factory functions for Listener events
    //////////////////////////////////////////////////////////////
    Object newServletContextEvent(ServletContextDuck ctx);

    Object newContextAttributeEvent(ServletContextDuck ctx, String name, Object value);

    Object newHttpSessionEvent(HttpSessionDuck session);

    Object newSessionBindingEvent(HttpSessionDuck session, String name, Object value);

    Object newServletRequestEvent(ServletContextDuck ctx, Object req);

    Object newServletRequestAttributeEvent(ServletContextDuck ctx, Object req, String name, Object value);

    Object newAsyncEvent(ASyncContextDuck ctx);

    Object newAsyncEvent(ASyncContextDuck ctx, Object req, Object res);

}
