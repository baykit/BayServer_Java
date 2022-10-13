package baykit.bayserver.docker.servlet.javax;

import baykit.bayserver.docker.servlet.duck.ASyncContextDuck;
import baykit.bayserver.docker.servlet.duck.HttpServletRequestHelper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JavaxHttpServletRequestHelper implements HttpServletRequestHelper {

    //////////////////////////////////////////////////////////////
    //  Helper methods for HttpServletRequest
    //////////////////////////////////////////////////////////////

    @Override
    public Object getAttribute(Object req, String name) {
        return ((HttpServletRequest)req).getAttribute(name);
    }

    @Override
    public void setAttribute(Object req, String name, String value) {
        ((HttpServletRequest)req).setAttribute(name, value);
    }

    @Override
    public void removeAttribute(Object req, String name) {
        ((HttpServletRequest)req).getAttribute(name);
    }

    @Override
    public String getRequestURI(Object req) {
        return ((HttpServletRequest)req).getRequestURI();
    }

    @Override
    public String getContextPath(Object req) {
        return ((HttpServletRequest)req).getContextPath();
    }

    @Override
    public String getPathInfo(Object req) {
        return ((HttpServletRequest)req).getPathInfo();
    }

    @Override
    public String getServletPath(Object req) {
        return ((HttpServletRequest)req).getServletPath();
    }

    @Override
    public String getQueryString(Object req) {
        return ((HttpServletRequest)req).getQueryString();
    }

    @Override
    public String getPathTranslated(Object req) {
        return ((HttpServletRequest)req).getPathTranslated();
    }

    @Override
    public ASyncContextDuck startAsync(Object req, Object res) {
        return  (ASyncContextDuck) ((HttpServletRequest)req).startAsync((HttpServletRequest)req, (HttpServletResponse)res);
    }

    @Override
    public boolean isAsyncSupported(Object req) {
        return ((HttpServletRequest)req).isAsyncSupported();
    }

    @Override
    public String getCharacterEncoding(Object req) {
        return ((HttpServletRequest)req).getCharacterEncoding();
    }


}
