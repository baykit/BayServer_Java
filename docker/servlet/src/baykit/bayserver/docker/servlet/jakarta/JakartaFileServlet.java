package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.docker.servlet.duck.FileServletDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class JakartaFileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        new FileServletDuck((ServletContextDuck)getServletContext()).doGet(req, res);
    }
}
