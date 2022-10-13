import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

public class Session extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session1 = req.getSession(false);
        HttpSession session2 = req.getSession(true);


        Integer integer = (Integer) session2.getValue("count");
        if (integer == null)
            integer = new Integer(0);

        integer = new Integer(integer.intValue() + 1);
        session2.putValue("count", integer);

        res.setContentType("text/html; charset=EUC_JP");
        PrintWriter w = res.getWriter();
        w.println("<html>");
        w.println("<body bgcolor=#eeeeff>");
        w.println("req.getSession(false)=" + session1);
        w.println("<P>");
        w.println("req.getSession(true)=" + session2);
        w.println("<P>");
        w.println("integer=" + integer);
        w.println("<P>");
        w.println("</body>");
        w.println("</html>");
    }
}
