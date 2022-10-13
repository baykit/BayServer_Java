import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.StringTokenizer;

public class AuthFilter implements Filter {

    String user;
    String pass;
    static String REQ_HDR = "Authorization";
    static String RES_HDR = "WWW-Authenticate";
    ServletContext ctx;

    @Override
    public void init(FilterConfig cfg) throws ServletException {
        user = cfg.getInitParameter("user");
        pass = cfg.getInitParameter("password");
        ctx = cfg.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest hreq = (HttpServletRequest)req;
        HttpServletResponse hres = (HttpServletResponse)res;

        boolean authorized = false;
        String auth = hreq.getHeader(REQ_HDR);
        if(auth != null) {
            if(auth.startsWith("Basic")) {
                byte[] decoded = Base64.getDecoder().decode(auth.substring(5).trim());
                auth = new String(decoded);
                ctx.log(auth);

                StringTokenizer st = new StringTokenizer(auth, ":");
                String u = "";
                if(st.hasMoreTokens())
                    u = st.nextToken();
                String p = "";
                if(st.hasMoreTokens())
                    p = st.nextToken();
                if(u.equals(user) && p.equalsIgnoreCase(pass))
                    authorized = true;
            }
        }

        if(!authorized) {
            hres.setHeader(RES_HDR, "Basic realm=\"Auth\"");
            hres.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
        else {
            chain.doFilter(req, res);
        }
    }


    @Override
    public void destroy() {

    }
}
