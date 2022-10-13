import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.*;
import javax.servlet.http.*;

public class Include extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        res.setContentType("text/html");

        PrintWriter out = res.getWriter();
        out.println("<html>");
        out.println("<body bgcolor=#eeeeff>");
        out.println("<center>");
        out.println("Here is included jsp");
        out.println("<hr>");
        RequestDispatcher rd = req.getRequestDispatcher("ShowQueryParams?hoge=huga");
        rd.include(req, res);
        out.println("<hr>");
        out.println("</body>");
        out.println("</html>");
    }
}



