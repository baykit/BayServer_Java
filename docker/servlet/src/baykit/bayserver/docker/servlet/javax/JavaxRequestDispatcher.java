package baykit.bayserver.docker.servlet.javax;

import baykit.bayserver.BayLog;
import baykit.bayserver.docker.servlet.ReqInfo;
import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.HttpServletResponseDuck;
import baykit.bayserver.docker.servlet.duck.RequestDispatcherDuck;
import baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.IOException;

class JavaxRequestDispatcher extends RequestDispatcherDuck implements RequestDispatcher {

    public JavaxRequestDispatcher(
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