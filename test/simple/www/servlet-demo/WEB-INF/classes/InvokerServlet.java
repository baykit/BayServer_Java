import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * A servlet to invoke another servlet.
 */
public class InvokerServlet extends HttpServlet {

    public static final String ATTR_SERVLET_PATH = "javax.servlet.include.servlet_path";
    public static final String ATTR_PATH_INFO = "javax.servlet.include.path_info";
    
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        //  We consider two type of RequestURI or PathInfo
        //     
        // 1. RequestURI : /context/servlet/ServletName?...
        //     CurrentServletPath: /servlet
        //     CurrentPathInfo: /ServletName
        //      -> NewPathInfo: null
        //         NewServletPath: /servlet/ServletName
        //
        // 2. RequestURI : /context/servlet/ServletName/RealPathInfo?...
        //     CurrentServletPath: /servlet
        //     CurrentPathInfo: /ServletName/RealPathInfo
        //      -> NewPathInfo: /RealPathInfo
        //         NewServletPath: /servlet/ServletName

        String currentPathInfo = req.getAttribute(ATTR_PATH_INFO) != null ? (String)req.getAttribute(ATTR_PATH_INFO) : req.getPathInfo();
        String servletPath = req.getServletPath() != null ? (String)req.getAttribute(ATTR_SERVLET_PATH) : req.getPathInfo();
        
        if(currentPathInfo == null || currentPathInfo.equals("")) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        int pos = currentPathInfo.indexOf("/", 1);
        String servletName, newServletPath, newPathInfo;

        if (pos == -1) {
            // Type 1
            servletName = currentPathInfo.substring(1);
            newPathInfo = null;
        } else {
            // Type 2
            servletName = currentPathInfo.substring(1, pos);
            newPathInfo = currentPathInfo.substring(pos);
        }
        newServletPath = servletPath + '/' + servletName;

        HttpServletRequest newRequest = new InvokerServletRequest(
                newServletPath, newPathInfo, req);



        /**
         * 1. Find servlet by name;
         * if not found, 2. Find servlet by class name
         */ 
        
        // 1. Find servlet by name
        RequestDispatcher dsp = getServletContext().getNamedDispatcher(servletName);
        
        if (dsp != null) {
            getServletContext().log("Found by servlet name: " + servletName);
            dsp.forward(newRequest, res);            
        }
        else {
            // 2. Find servlet by class name
            Servlet newServlet;
            try {
                newServlet = (Servlet)Class.forName(servletName).newInstance();
            }
            // ClassNotFoundException must be reported as NOT FOUND
            catch (ClassNotFoundException e) {
                e.printStackTrace();
                res.sendError(
                        HttpServletResponse.SC_NOT_FOUND, 
                        req.getRequestURI() + "(" + e + ")");
                return;
            }
            // Another exception must be reported
            catch (Throwable e) {
                throw new ServletException(e);
            }

            if (newServlet == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
            }
            else {
                getServletContext().log("Found by servlet class: " + newServlet);
                newServlet.init(getServletConfig());
                newServlet.service(newRequest, res);
            }
        }
    }
}