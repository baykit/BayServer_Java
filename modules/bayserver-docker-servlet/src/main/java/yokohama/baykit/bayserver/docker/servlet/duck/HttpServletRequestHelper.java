package yokohama.baykit.bayserver.docker.servlet.duck;

/**
 * Helper methods for HttpServletRequest
 */
public interface HttpServletRequestHelper {
    //////////////////////////////////////////////////////////////
    //  Helper methods for HttpServletRequest
    //////////////////////////////////////////////////////////////

    Object getAttribute(Object req, String name);

    void setAttribute(Object req, String name, String value);

    void removeAttribute(Object req, String name);

    String getRequestURI(Object req);

    String getContextPath(Object req);

    String getPathInfo(Object req);

    String getServletPath(Object req);

    String getQueryString(Object req);

    String getPathTranslated(Object req);

    ASyncContextDuck startAsync(Object req, Object res);

    boolean isAsyncSupported(Object req);

    String getCharacterEncoding(Object req);

}
