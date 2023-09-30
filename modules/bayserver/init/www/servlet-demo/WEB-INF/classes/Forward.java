import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.*;
import javax.servlet.http.*;

public class Forward extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
    	
        RequestDispatcher rd =
                req.getRequestDispatcher("ShowQueryParams?hoge=huga");

        //RequestDispatcher rd = 
        //    req.getRequestDispatcher("/servlets/hello.jsp");
        rd.forward(req, res);
    }
}



