package yokohama.baykit.bayserver.docker.servlet.duck;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.URLDecoder;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * HttpServletResponse implementation for duck typing
 */
public abstract class HttpServletResponseDuck {

    /** True if use output stream to write */
    public boolean useStream = false;

    /** True if use writer to write */
    public boolean useWriter = false;

    /** Print writer */
    private PrintWriter pw;

    /** Tour instance */
    public final Tour tour;

    /** Tour id */
    public final int tourId;
    
    /** Servlet docker */
    final ServletDocker docker;

    /** Committed flag */
    private boolean committed = false;

    /** Current locale */
    private Locale locale;
    
    /** Request  */
    HttpServletRequestDuck req;
    
    /** ServletOutputStream instance */
    OutputStream out;

    /** Cookie list */
    private final ArrayList<Object> cookies = new ArrayList<>();

    public HttpServletResponseDuck(Tour tour, ServletDocker docker) {
        this.tour = tour;
        this.tourId = tour.tourId;
        this.docker = docker;
    }

    public void setRequest(HttpServletRequestDuck req) {
        this.req = req;
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////
    public final void addCookieObject(Object cookie) {
        cookies.add(cookie);
    }

    public final boolean containsHeader(String name) {
        return tour.res.headers.contains(name);
    }

    public final String encodeURL(String url) {
        return encodeUrl(url);
    }

    public final String encodeRedirectURL(String url) {
        return encodeRedirectUrl(url);
    }

    public final String encodeUrl(String url) {
        HttpSessionDuck ses = req.getSessionDuck(false);
        if(ses == null)
            return url;
        else if (req.isRequestedSessionIdFromCookie())
            return url;
        else
            return encodeUrl(url, ses.getId());
    }

    public final String encodeRedirectUrl(String url) {
        return encodeURL(url);
    }

    public final void sendError(int sc, String msg) throws IOException {
        BayLog.error(req.tour + " sendError: code=" + sc + " msg=" + msg);
        if(isCommitted())
            throw new IOException("Cannot send error since header is commited: " + sc + ": " + msg);

        setCookieHeaders();
        setStatus(sc);

        if(req.getAttribute(ServletDocker.ATTR_ERR_PAGE) == null) {
            // check error-page
            String errorPageLoc = docker.errorPageStore.find(sc);
            if (errorPageLoc != null) {
                RequestDispatcherDuck d = docker.ctx.getRequestDispatcherDuck(errorPageLoc);
                if (d != null) {
                    try {
                        req.setAttribute(ServletDocker.ATTR_ERR_PAGE, errorPageLoc);
                        d.forwardDuck(req, this);
                    } catch (ServletExceptionDuck e) {
                        throw new IOException(e.getServletException());
                    } finally {
                        req.removeAttribute(ServletDocker.ATTR_ERR_PAGE);
                        return;
                    }
                }
            }
        }

        String content = "<html><body><h1>" + HttpStatus.description(sc) + "</h1></body></html>";
        byte[] bytes;
        try {
            bytes = content.getBytes(getCharacterEncoding());
        }
        catch(UnsupportedEncodingException e) {
            bytes = content.getBytes();
        }
        sendHeaders();
        sendContent(bytes, 0, bytes.length);
    }

    public final void sendError(int sc) throws IOException {
        sendError(sc, "");
    }

    public final void sendRedirect(String location) throws IOException {
        if(isCommitted())
            throw new IOException("Cannot redirect since header is commited: " + location);

        URL u;
        try {
            u = new URL(location);
        } catch (MalformedURLException e) {
            try {
                URL base = new URL(req.getScheme(), req.getServerName(), req.getServerPort(), req.getRequestURI());
                u = new URL(base, location);
            } catch (MalformedURLException me) {
                me.printStackTrace();
                throw new Error(me.toString());
            }
        }

        setCookieHeaders();
        tour.res.headers.setStatus(HttpStatus.MOVED_TEMPORARILY);
        tour.res.headers.set(Headers.LOCATION, u.toExternalForm());
        sendHeaders();
    }

    public final void setDateHeader(String name, long date) {
        tour.res.headers.setDate(name, date);
    }

    public final void addDateHeader(String name, long date) {
        tour.res.headers.addDate(name, date);
    }

    public final void setHeader(String name, String value) {
        tour.res.headers.set(name, value);
    }

    public final void addHeader(String name, String value) {
        tour.res.headers.add(name, value);
    }

    public final void setIntHeader(String name, int value) {
        tour.res.headers.setInt(name, value);
    }

    public final void addIntHeader(String name, int value) {
        tour.res.headers.addInt(name, value);
    }

    public final void setStatus(int sc) {
        tour.res.headers.setStatus(sc);
    }

    public final void setStatus(int sc, String sm) {
        tour.res.headers.setStatus(sc);
    }

    public final int getStatus() {
        return tour.res.headers.status();
    }

    public final String getHeader(String name) {
        return tour.res.headers.get(name);
    }

    public final Collection<String> getHeaders(String name) {
        return tour.res.headers.headerValues(name);
    }

    public final Collection<String> getHeaderNames() {
        return tour.res.headers.headerNames();
    }

    public final String getCharacterEncoding() {
        if(tour.res.charset() != null)
            return tour.res.charset();
        else
            return docker.ctx.getResponseCharacterEncoding();
    }

    public final String getContentType() {
        return tour.res.headers.contentType();
    }

    public final OutputStream getOutputStreamObject() {
        if (useWriter) {
            throw new IllegalStateException(
                    "getWriter() has already been called.");
        }
        useStream = true;
        if(out == null) {
            out = docker.duckFactory.newServletOutputStream(this, guessCharset());
        }
        return out;
    }

    public final PrintWriter getWriter() throws IOException {
        if (useStream) {
            throw new IllegalStateException(
                    "getOutputStream() has already beed called.");
        }

        if (pw == null) {
            OutputStream sos = docker.duckFactory.newServletOutputStream(this, null);
            OutputStreamWriter osw = new OutputStreamWriter(sos, guessCharset());
            pw = new PrintWriter(osw, true);
            useWriter = true;
        }
        return pw;
    }

    public final void setCharacterEncoding(String encoding) {
        tour.res.setCharset(encoding);
    }

    public final void setContentLength(int len) {
        tour.res.headers.setContentLength(len);
    }

    public final void setContentLengthLong(long len) {
        tour.res.headers.setContentLength(len);
    }

    public final void setContentType(String type) {
        StringTokenizer st = new StringTokenizer(type, ";");
        while(st.hasMoreTokens()) {
            String token = st.nextToken();
            String charsetDef = "charset=";
            if(token.startsWith(charsetDef)) {
                String charset = token.substring(charsetDef.length());
                setCharacterEncoding(charset);
            }
        }
        tour.res.headers.setContentType(type);
    }

    public final void setBufferSize(int i) {

    }

    public final int getBufferSize() {
        return 0;
    }

    public final void flushBuffer() throws IOException {
        // we assume that the buffer is always commited on the first write.
        // (i.e. buffer size is zero)

        // just to confirm output be commited.
        if (useWriter)
            pw.flush();
        else
            out.flush();
    }

    public final void resetBuffer() {
        if (isCommitted())
            throw new IllegalStateException(
                    "Unable to reset buffer, already committed.");
    }

    public final boolean isCommitted() {
        return tour.res.headerSent();
    }

    public final void reset() {
        if (isCommitted())
            throw new IllegalStateException(
                    "Unable to reset buffer, already committed.");
    }

    public final void setLocale(Locale locale) {
        this.locale = locale;
        String language = locale.getLanguage();
        if ((language != null) && (language.length() > 0)) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(language);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            setHeader("Content-Language", value.toString());
        }
    }

    public final Locale getLocale() {
        return locale;
    }


    //////////////////////////////////////////////////////////////////////
    // custom methods
    //////////////////////////////////////////////////////////////////////

    private String encodeUrl(String url, String sesId) {

        int sharpPos = url.indexOf('#');
        if (sharpPos == 0)
            return url;

        int questionPos = url.indexOf('?');

        String nvpair = ServletDocker.PARAM_SESID + '=' + sesId;

        StringBuilder sb = new StringBuilder(url);

        if (questionPos != -1)
            sb.insert(questionPos + 1, nvpair + "&#38;");
        else
            sb.append('?').append(nvpair);

        return sb.toString();
    }

    private String guessCharset() {
        String charset = getCharacterEncoding();
        if(StringUtil.empty(charset))
            return "UTF-8";
        else
            return charset;
    }

    public boolean useStream() {
        return useStream;
    }

    public boolean useWriter() {
        return useWriter;
    }

    /**
     * Make a cookie header
     */
    private void addCookieHeader(Object cookie, boolean decodeTilde) {
        String value = docker.cookieHelper.getName(cookie) + "=" + docker.cookieHelper.getValue(cookie) + ";";

        if (docker.cookieHelper.getMaxAge(cookie) >= 0) {
            long now = new Date().getTime();
            value += "expires="
                    + Headers.dateFormat.format(new Date(docker.cookieHelper.getMaxAge(cookie) * 1000 + now))
                    + ";";
        }

        value += "version=" + docker.cookieHelper.getVersion(cookie) + ";";

        String path = docker.cookieHelper.getPath(cookie);
        if (path != null) {
            if(decodeTilde)
                path = URLDecoder.decodeTilde(path);
            value += "path=" + path + ";";
        }

        if (docker.cookieHelper.getDomain(cookie) != null)
            value += "domain=" + docker.cookieHelper.getDomain(cookie) + ";";

        if (docker.cookieHelper.getSecure(cookie))
            value += "secure;";

        addHeader("Set-Cookie", value);
    }

    /**
     * Add cookie headers * This method is called before sendHeaders()
     */
    public final void setCookieHeaders() {
        Object[] cookieObjects = docker.cookieHelper.toArray(cookies);
        if(cookieObjects == null)
            return;
        for (Object cookieObject : cookieObjects) {
            addCookieHeader(cookieObject,
                    BayServer.decodeTilde);
        }
    }

    public final void sendHeaders() throws IOException {
        setCookieHeaders();

        tour.res.setConsumeListener(ContentConsumeListener.devNull);
        tour.res.sendHeaders(tourId);
    }

    public void sendContent(byte[] b, int off, int len) throws IOException {
        tour.res.sendResContent(tourId, b, off, len);
        while(!tour.res.available) {
            try {
                Thread.sleep(100);
            }
            catch(InterruptedException e) {
                throw new IOException(e);
            }
        }
    }
}