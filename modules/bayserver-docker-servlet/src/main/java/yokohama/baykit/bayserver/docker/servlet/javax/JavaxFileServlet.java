package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.FileServletDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class JavaxFileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        new FileServletDuck((ServletContextDuck)getServletContext()).doGet(req, res);
    }
}
