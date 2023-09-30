import java.io.IOException;
import java.util.Enumeration;
import javax.servlet.*;
import javax.servlet.http.*;

public class InitParams extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        res.setContentType("text/html");

        ServletOutputStream out = res.getOutputStream();
        out.println("<html>");
        out.println("<body bgcolor='#eeeeff'>");
        out.println("<h2>Context params</h2>");
        out.println("<table cellpadding=\"5\" border=\"1\" bgcolor=\"ddddff\">");
        out.println("<tr><th>name</th><th>value</th></tr>");
		
        Enumeration<String> e = getServletContext().getInitParameterNames();
		while (e.hasMoreElements()) {
			String name = e.nextElement();
			String value = getServletContext().getInitParameter(name);
			out.println("<tr>");
			out.println("<td>" + name + "</td>");
			out.println("<td>" + value + "</td>");
			out.println("</tr>");
		}
        out.println("</table>");

        out.println("<h2>Servlet Init params</h2>");
        out.println("<table cellpadding=\"5\" border=\"1\" bgcolor=\"ddddff\">");
        out.println("<tr><th>name</th><th>value</th></tr>");
		e = getInitParameterNames();
		while (e.hasMoreElements()) {
			String name = (String) e.nextElement();
			String value = getInitParameter(name);
			out.println("<tr>");
			out.println("<td>" + name + "</td>");
			out.println("<td>" + value + "</td>");
			out.println("</tr>");
		}
        out.println("</table>");
        out.println("</body>");
        out.println("</html>");
    }
}



