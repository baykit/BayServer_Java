import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class Hello extends HttpServlet
{
	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		res.setContentType("text/html");

		PrintWriter out = res.getWriter();
		out.println("<html>");
		out.println("<body bgcolor=#eeeeff>");
		out.println("<center>");
		out.println("<h1>Hello BayServer</h1>");
		out.println("</center>");
		out.println("</body>");
		out.println("</html>");
	}
}



