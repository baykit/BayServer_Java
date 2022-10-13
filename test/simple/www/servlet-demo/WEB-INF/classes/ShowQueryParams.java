import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShowQueryParams extends HttpServlet {

    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        res.setContentType("text/html");
        PrintWriter out = res.getWriter();

        out.println("<html>");
        out.println("<body bgcolor='#eeeeff'>");

        out.println("<table border=1 cellpadding=3>");
        out.println("<tr>");
        out.println("<th>Parameter Name</th>");
        out.println("<th>Parameter Value</th>");
        out.println("</tr>");

        Enumeration names = req.getParameterNames();

        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();

            String values[] = (String[]) req.getParameterValues(name);

            out.println("<tr>");
            out.println("<td ROWSPAN=" + values.length + ">" + name + "</td>");
            for (int i = 0; i < values.length; i++) {
                if (i != 0)
                    out.println("<tr>");
                out.println("<td>" + values[i] + "</td>");
                out.println("</tr>");
            }
        }

        out.println("</table>");
        out.println("</body>");
        out.println("</html>");
    }

    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        doGet(req, res);
    }
}
