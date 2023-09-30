package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.ASyncContextDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import jakarta.servlet.*;

public class JakartaAsyncContext extends ASyncContextDuck implements AsyncContext {


    public JakartaAsyncContext(Object req, Object res, boolean original, ServletDocker docker) {
        super(req, res, original, docker);
    }

    @Override
    public ServletRequest getRequest() {
        return (ServletRequest) getRequestObject();
    }

    @Override
    public ServletResponse getResponse() {
        return (ServletResponse) getResponseObject();
    }

    @Override
    public void dispatch(ServletContext ctx, String path) {
        dispatch((ServletContextDuck)ctx, path);

    }

    @Override
    public void addListener(AsyncListener alis) {
        addListenerDuck(alis);
    }

    @Override
    public void addListener(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {

    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> aClass) throws ServletException {
        try {
            return (T)createListenerDuck(aClass);
        }
        catch(ServletExceptionDuck e) {
            throw new ServletException(e.getCause());
        }
    }

    @Override
    public String ASYNC_REQUEST_URI() {
        return ASYNC_REQUEST_URI;
    }

    @Override
    public String ASYNC_CONTEXT_PATH() {
        return ASYNC_CONTEXT_PATH;
    }

    @Override
    public String ASYNC_PATH_INFO() {
        return ASYNC_PATH_INFO;
    }

    @Override
    public String ASYNC_SERVLET_PATH() {
        return ASYNC_SERVLET_PATH;
    }

    @Override
    public String ASYNC_QUERY_STRING() {
        return null;
    }
}
