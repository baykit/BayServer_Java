import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

public class Redirect1 extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession(true);
        session.setAttribute("name", "BayServer(Session attribute)");

        
        res.setContentType("text/html; charset=EUC_JP");
        PrintWriter w = res.getWriter();
        w.println("<html>");
        w.println("<body bgcolor=#eeeeff>");

        w.println("<A HREF=\"" + res.encodeRedirectURL("Redirect2") + "\">");
        w.println("Next Servlet");
        w.println("</A>");
        w.println("</body>");
        w.println("</html>");
    }
}
