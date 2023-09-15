import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class AsyncServlet extends HttpServlet {

    class AsyncTask implements Runnable {

        AsyncContext ctx;

        public AsyncTask(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {

            HttpServletResponse res = (HttpServletResponse)ctx.getResponse();
            int sec = 5;
            try {
                try {
                    Thread.sleep(sec * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                res.setContentType("text/html");
                PrintWriter w = res.getWriter();
                w.println("<html><body>");
                w.println("slept " + sec + " seconds");
                w.println("</body></html>");
                w.flush();

                ctx.complete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        AsyncContext ctx = req.startAsync();

        ctx.start(new AsyncTask(ctx));
    }
}



