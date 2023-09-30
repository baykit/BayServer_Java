package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.RequestDispatcherDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

class JakartaNamedDispatcher extends RequestDispatcherDuck implements RequestDispatcher {


    public JakartaNamedDispatcher(
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