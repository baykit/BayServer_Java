import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(name = "counter", urlPatterns = { "/counter" })
public class Counter extends HttpServlet {
	protected void doGet(HttpServletRequest req, HttpServletResponse res)
			throws IOException {

		String countStr = "0";
		Cookie cookies[] = req.getCookies();
		if (cookies != null) {
			for (int i = 0; i < cookies.length; i++) {
				if (cookies[i].getName().equals("count")) {
					Cookie c = (Cookie) cookies[i];
					countStr = c.getValue();
				}
			}
		}

		int count = Integer.parseInt(countStr);
		count++;

		Cookie c = new Cookie("count", Integer.toString(count));
		res.addCookie(c);

		PrintWriter out = res.getWriter();
		out.println("<html>");
		out.println("<body bgcolor=#eeeeff>");
		out.println("Counter = " + count);
		out.println("</body>");
		out.println("</html>");
	}
}
