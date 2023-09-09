package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.ReqInfo;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.RequestDispatcherDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

class JakartaRequestDispatcher extends RequestDispatcherDuck implements RequestDispatcher {

    public JakartaRequestDispatcher(
            Object servlet,
            ServletDocker docker,
            ReqInfo orginfo,
            String ctxPath,
            String reqUri,
            String servletPath,
            String pathInfo,
            String queryString) {
        super(servlet, docker, orginfo, ctxPath, reqUri, servletPath, pathInfo, queryString);
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