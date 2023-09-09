package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.RequestDispatcherDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.IOException;

class JavaxNamedDispatcher extends RequestDispatcherDuck implements RequestDispatcher {


    public JavaxNamedDispatcher(
            Object servlet, 
            ServletDocker docker, 
            String ctxPath) {
        super(servlet, docker, null, ctxPath, null, null, null, null);
    }

    @Override
    public void forward(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        try {
            forwardDuck(req, res);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public void include(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        try {
            includeDuck(req, res);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }
}