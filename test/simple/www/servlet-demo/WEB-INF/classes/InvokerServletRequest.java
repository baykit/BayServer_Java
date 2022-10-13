import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class InvokerServletRequest extends HttpServletRequestWrapper {

    // New servlet path
    private String servletPath;

    // New path info
    private String pathInfo;

    InvokerServletRequest(String servletPath,
            String pathInfo,
            HttpServletRequest req) {

        super(req);
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
    }

    public String getServletPath() {
        return servletPath;
    }

    public String getPathInfo() {
        return pathInfo;
    }
}