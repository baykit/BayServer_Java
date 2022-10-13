//
// Hello.java
//

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.*;
import javax.sql.*;
import java.sql.*;

import javax.naming.*;


public class ResourceServlet extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        res.setContentType("text/html");

        try {
            Context initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            DataSource ds = (DataSource) envCtx.lookup("jdbc/MyDB");

            Connection conn = ds.getConnection();

            PrintWriter out = res.getWriter();
            out.println("<html>");
            out.println("<body bgcolor=#eeeeff>");

            PreparedStatement ps =
                    conn.prepareStatement("select * from open_source_softwares");
            ResultSet rs = ps.executeQuery();

            out.println("<TABLE>");
            out.println("<TR><TH>ORG</TH><TH>NAME</TH></TR>");
            while (rs.next()) {
                out.println("<TR>");
                out.println("<TD>" + rs.getString("ORG") + "</TD>");
                out.println("<TD>" + rs.getString("NAME") + "</TD>");
                out.println("</TR>");
            }
            out.println("</TABLE>");
            out.println("</body>");
            out.println("</html>");

            rs.close();
            ps.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}



