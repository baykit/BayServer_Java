package baykit.bayserver.docker.servlet.duck;

import baykit.bayserver.BayLog;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.servlet.*;
import baykit.bayserver.docker.servlet.javax.JavaxHttpServletResponse;
import baykit.bayserver.util.LocaleUtil;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * HttpServletRequest implementation for duck typing
 */
public abstract class HttpServletRequestDuck {

    /** True if use input stream to read */
    boolean useStream = false;

    /** True if use reader to read */
    boolean useReader = false;

    /** ServletInputStream instance */
    InputStream in;

    /** Tour instance */
    Tour tour;

    /** Request attributes */
    HashMap<String, Object> atrs = new HashMap<>();

    /** Servlet docker */
    public ServletDocker docker;

    /** Request info */
    public ReqInfo reqInfo;

    /** Response */
    HttpServletResponseDuck res;

    /** Async support */
    boolean asyncSupported;

    /** Async context */
    ASyncContextDuck asyncCtx;

    /** Accepted locale list */
    ArrayList<Locale> locales;

    /** Current available session */
    HttpSessionDuck curSession;

    /**
     * Constructor
     */
    public HttpServletRequestDuck(Tour tour, ReqInfo reqInfo, HttpServletResponseDuck res, ServletDocker docker, boolean asyncSupported) {
        this.tour = tour;
        this.docker = docker;
        this.res = res;
        this.reqInfo = reqInfo;
        this.asyncSupported = asyncSupported;
        this.curSession = reqInfo.session;

        // Invoke event handlers
        ArrayList<EventListener> listeners = docker.listenerStore.getListeners(docker.listenerHelper.servletRequestListenerClass());
        if(!listeners.isEmpty()) {
            Object reqEvt = docker.duckFactory.newServletRequestEvent(docker.ctx, this);
            for(Object listener : listeners) {
                docker.listenerHelper.requestInitialized(listener, reqEvt);
            }
        }
    }

    public final Tour getTour() {
        return tour;
    }

    public final ReqInfo getReqInfo() {
        return reqInfo;
    }

    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    public void setInputStream(InputStream in) {
        this.in = in;
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////

    public final String getAuthType() {
        return null;
    }

    public final Object[] getCookieObjects() {
        return reqInfo.cookies;
    }

    public final long getDateHeader(String name) {
        return tour.req.headers.getDate(name);
    }

    public final String getHeader(String name) {
        return tour.req.headers.get(name);
    }

    public final Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(tour.req.headers.headerValues(name));
    }

    public final Enumeration<String> getHeaderNames() {
        return Collections.enumeration(tour.req.headers.headerNames());
    }

    public final int getIntHeader(String name) {
        return tour.req.headers.getInt(name);
    }

    public final String getMethod() {
        return tour.req.method;
    }

    public final String getPathInfo() {
        return reqInfo.pathInfo;
    }

    public final String getPathTranslated() {
        if(getPathInfo() == null)
            return null;
        else
            return getRealPath(getPathInfo());
    }

    public final String getContextPath() {
        return docker.ctx.getContextPath();
    }

    public final String getQueryString() {
        return reqInfo.queryString;
    }

    public final String getRemoteUser() {
        return null;
    }

    public final boolean isUserInRole(String s) {
        return false;
    }

    public final Principal getUserPrincipal() {
        return null;
    }

    public final String getRequestedSessionId() {
        return reqInfo.session != null ? reqInfo.session.getId() : null;
    }

    public final String getRequestURI() {
        return reqInfo.reqUri;
    }

    public final StringBuffer getRequestURL() {

        StringBuffer result = new StringBuffer();
        String uri = getRequestURI();

        result.append(getScheme());
        result.append("://");
        result.append(getServerName());
        result.append(":");
        result.append(getServerPort());
        if (!uri.startsWith("/")) {
            result.append("/");
        }
        result.append(uri);

        return result;
    }

    public final String getServletPath() {
        return reqInfo.servletPath;
    }

    public final HttpSessionDuck getSessionDuck(boolean create) {
        if(curSession != null && curSession.isValid())
            return curSession;

        if(create) {
            HttpSessionDuck ses = docker.sessionStore.createSession();
            Object cookie = docker.cookieHelper.newCookie(ServletDocker.PARAM_SESID, ses.getId());
            docker.cookieHelper.setPath(cookie, docker.ctx.getContextPath());
            res.addCookieObject(cookie);
            curSession = ses;
            return ses;
        }
        return null;
    }

    public final HttpSessionDuck getSessionDuck() {
        return getSessionDuck(true);
    }

    public final String changeSessionId() {
        if(curSession == null && !curSession.isValid())
            throw new IllegalStateException("No valid session");

        curSession.chnageId(docker.sessionStore.newSessionId());
        return curSession.id;
    }

    public final boolean isRequestedSessionIdValid() {
        return reqInfo.session != null && reqInfo.session.valid;
    }

    public final boolean isRequestedSessionIdFromCookie() {
        return reqInfo.cookieSession;
    }

    public final boolean isRequestedSessionIdFromURL() {
        return !reqInfo.cookieSession;
    }

    public final boolean isRequestedSessionIdFromUrl() {
        // deprecated method
        return isRequestedSessionIdFromURL();
    }

    public final boolean authenticateDuck(JavaxHttpServletResponse httpServletResponse)
            throws ServletExceptionDuck {
        return false;
    }

    public final void loginDuck(String username, String password) throws ServletExceptionDuck  {

    }

    public final void logoutDuck() throws ServletExceptionDuck  {

    }

    public Collection<Object> getPartObjects() throws ServletExceptionDuck  {
        return new ArrayList<>();
    }

    public final Object getPartObject(String name) throws ServletExceptionDuck  {
        return null;
    }

    public final  <T> T upgradeInDuck(Class<T> aClass)
            throws ServletExceptionDuck  {
        return null;
    }

    public final Object getAttribute(String name) {
        if(BayLog.isDebugMode())
            BayLog.debug(tour+ " getAttribute: " + name + "=" + atrs.get(name));
        return atrs.get(name);
    }

    public final Enumeration<String> getAttributeNames() {
        return Collections.enumeration(atrs.keySet());
    }

    public final String getCharacterEncoding() {
        if(tour.req.charset() != null)
            return tour.req.charset();
        else
            return docker.ctx.getRequestCharacterEncoding();
    }

    public final void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
        byte[] test = "".getBytes(encoding);
        tour.req.setCharset(encoding);
    }

    public final int getContentLength() {
        return tour.req.headers.contentLength();
    }

    public final long getContentLengthLong() {
        return tour.req.headers.contentLength();
    }

    public final String getContentType() {
        return tour.req.headers.contentType();
    }

    public final InputStream getInputStreamObject() {
        if (useReader) {
            throw new IllegalStateException("getReader() was already called.");
        }
        useStream = true;
        return docker.duckFactory.newServletInputStream(this);
    }

    public final String getParameter(String name) {
        String[] vals = getParameterValues(name);
        if(vals == null || vals.length == 0)
            return null;
        else
            return vals[0];
    }

    public final Enumeration<String> getParameterNames() {
        Map<String, String[]> map = getParameterMap();
        return Collections.enumeration(map.keySet());
    }

    public final String[] getParameterValues(String name) {
        Map<String, String[]> map = getParameterMap();
        return map.get(name);
    }

    public final Map<String, String[]> getParameterMap() {
        InputStream postIn = null;
        String ctype = getContentType();
        if(ctype != null && ctype.startsWith("application/x-www-form-urlencoded"))
            postIn = in;
        Parameters params = reqInfo.getParams(getCharacterEncoding(), postIn);
        return params.paramMap;
    }

    public final String getProtocol() {
        return tour.req.protocol;
    }

    public final String getScheme() {
        return reqInfo.scheme;
    }

    public final String getServerName() {
        return reqInfo.host;
    }

    public final int getServerPort() {
        return reqInfo.port;
    }

    public final BufferedReader getReader() throws IOException {
        if (useStream) {
            throw new IllegalStateException(
                    "getInputStream() was already called.");
        }

        InputStream is = getInputStreamObject();
        InputStreamReader isr = new InputStreamReader(is, getCharacterEncoding());

        useReader = true;
        return new BufferedReader(isr);
    }

    public final String getRemoteAddr() {
        return tour.req.remoteAddress;
    }

    public final String getRemoteHost() {
        return tour.req.remoteHost();
    }

    public final void setAttribute(String name, Object value) {
        if(BayLog.isDebugMode())
            BayLog.debug(tour + " setAttribute: " + name + "=" + value);
        boolean update = atrs.containsKey(name);
        atrs.put(name, value);

        // Invoke event handlers
        ArrayList<EventListener> listeners = docker.listenerStore.getListeners(docker.listenerHelper.servletRequestAttributeListenerClass());
        if(!listeners.isEmpty()) {
            Object atrEvt = docker.duckFactory.newServletRequestAttributeEvent(docker.ctx, this, name, value);
            for(Object listener : listeners) {
                if(update)
                    docker.listenerHelper.requestAttributeReplaced(listener, atrEvt);
                else
                    docker.listenerHelper.requestAttributeAdded(listener, atrEvt);
            }
        }
    }

    public final void removeAttribute(String name) {
        atrs.remove(name);

        // Invoke event handlers
        ArrayList<EventListener> listeners = docker.listenerStore.getListeners(docker.listenerHelper.servletRequestAttributeListenerClass());
        if(!listeners.isEmpty()) {
            Object atrEvt = docker.duckFactory.newServletRequestAttributeEvent(docker.ctx, this, name, null);
            for(Object listener : listeners) {
                docker.listenerHelper.requestAttributeRemoved(listener, atrEvt);
            }
        }
    }

    public final Locale getLocale() {
        return getLocales().nextElement();
    }

    /**
     *  Get Locale list from Accept Language
     *  From RFC 2616
     *     Accept-Language = "Accept-Language" ":"
     *                      1#( language-range [ ";" "q" "=" qvalue ] )
     *     language-range  = ( ( 1*8ALPHA *( "-" 1*8ALPHA ) ) | "*" )
     *
     * @return Enumeration of Locale
     */
    public final Enumeration<Locale> getLocales() {
        if(locales == null) {
            locales = new ArrayList<>();
            String val = getHeader("Accept-Language");
            if(val != null) {
                StringTokenizer st = new StringTokenizer(val, ",");
                while(st.hasMoreTokens()) {
                    String t = st.nextToken();
                    StringTokenizer st2 = new StringTokenizer(t, ";");
                    String loc = st2.nextToken().trim();
                    locales.add(LocaleUtil.parseLocale(loc));
                }
            }
            if(locales.isEmpty()) {
                locales.add(Locale.getDefault());
            }
        }

        return Collections.enumeration(locales);

    }

    public final boolean isSecure() {
        return tour.isSecure;
    }

    public final RequestDispatcherDuck getRequestDispatcherDuck(String path) {
        if(path == null)
            throw new NullPointerException();

        // make path from context root
        String pathInCtx = path;
        if(!path.startsWith("/")) {
            // Path is the relative path from servlet, so translate it to relative path from context
            String svtPath = reqInfo.servletPath;
            int pos = svtPath.lastIndexOf('/');
            if(pos > 0) {
                svtPath = svtPath.substring(0, svtPath.length() - pos);
            }
            pathInCtx = svtPath + "/" + path;
        }

        return docker.ctx.getRequestDispatcherDuck(pathInCtx);
    }

    public final String getRealPath(String rpath) {
        File f = new File(docker.town.location(), rpath);
        return f.getPath();
    }

    public final int getRemotePort() {
        return tour.req.remotePort;
    }

    public final String getLocalName() {
        return tour.req.serverName;
    }

    public final String getLocalAddr() {
        return tour.req.serverAddress;
    }

    public final int getLocalPort() {
        return tour.req.serverPort;
    }

    public final ServletContextDuck getServletContextDuck() {
        return docker.ctx;
    }

    public final ASyncContextDuck startAsyncDuck() throws IllegalStateException {
        return startAsyncDuck(this, res);
    }

    public final ASyncContextDuck startAsyncDuck(HttpServletRequestDuck req, HttpServletResponseDuck res)
            throws IllegalStateException {
        if(!asyncSupported)
            throw new IllegalStateException("Async is not supported on this request");
        if(asyncCtx != null)
            return asyncCtx;

        asyncCtx = docker.duckFactory.newAsyncContext(this, res, (req == this && res == this.res), docker);
        return asyncCtx;
    }

    public final boolean isAsyncStarted() {
        return asyncCtx != null && asyncCtx.started;
    }

    public final boolean isAsyncSupported() {
        return asyncSupported;
    }

    public final ASyncContextDuck getAsyncContextDuck() {
        return asyncCtx;
    }

    public final Object getDispatcherTypeObject() {
        return null;
    }


    //////////////////////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////////////////////
}