package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Request wrapper for RequestDispatcher.forward()
 */
class JakartaForwardRequest extends JakartaIncludeRequest {
    
    /** new request uri */
    String reqUri;
    
    /** new path info */
    String pathInfo;
    
    /** new servlet path */
    String servletPath;

    /** servlet context */
    ServletContextDuck ctx;

    public JakartaForwardRequest(
            HttpServletRequest request,
            ServletContextDuck ctx,
            String reqUri,
            String pathInfo, 
            String servletPath,
            String queryString) {
        super(request, queryString);
        this.ctx = ctx;
        this.reqUri = reqUri;
        this.pathInfo = pathInfo;
        this.servletPath = servletPath;
    }

    @Override
    public String getRequestURI() {
        return reqUri;
    }
    
    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public String getPathTranslated() {
        if(getPathInfo() == null)
            return null;
        else
            return getRealPath(getPathInfo());
    }
}
