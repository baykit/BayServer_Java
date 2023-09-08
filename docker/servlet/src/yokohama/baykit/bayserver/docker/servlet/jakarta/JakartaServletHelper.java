package yokohama.baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.*;
import jakarta.servlet.*;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletConfigDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletHelper;

import java.io.IOException;

public class JakartaServletHelper implements ServletHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for Servlet
    //////////////////////////////////////////////////////////////


    @Override
    public void service(Object servlet, Object req, Object res) throws ServletExceptionDuck, IOException {
        try {
            ((Servlet)servlet).service((ServletRequest)req, (ServletResponse)res);
        } catch (ServletException e) {
            throw new ServletExceptionDuck(e);
        }
    }

    @Override
    public void initServlet(Object servlet, ServletConfigDuck cfg) throws ServletExceptionDuck {
        try {
            if(!(servlet instanceof Servlet))
                throw new ServletException("Servlet is not instance of " + Servlet.class.getName() + ":" + servlet.getClass().getName());
            ((Servlet)servlet).init((JakartaServletConfig)cfg);
        } catch (ServletException e) {
            throw new ServletExceptionDuck(e);
        }
    }


}

