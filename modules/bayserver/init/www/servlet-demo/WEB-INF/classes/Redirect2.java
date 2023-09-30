import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

public class Redirect2 extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession(true);

        res.setContentType("text/html; charset=EUC_JP");
        PrintWriter w = res.getWriter();
        w.println("<html>");
        w.println("<body bgcolor=#eeeeff>");
        w.println("SessionId = " + req.getRequestedSessionId());
        w.println("<P>");
        w.println("SessionId From Url = " + req.isRequestedSessionIdFromURL());
        w.println("<P>");
        w.println("name = " + session.getAttribute("name"));
        w.println("<P>");
        w.println("</body>");
        w.println("</html>");
    }
}
