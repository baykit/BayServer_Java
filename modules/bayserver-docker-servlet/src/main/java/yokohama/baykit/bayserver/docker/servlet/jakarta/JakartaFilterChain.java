package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.*;
import jakarta.servlet.*;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterChainDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;

import java.io.IOException;
import java.util.Iterator;

class JakartaFilterChain extends FilterChainDuck implements FilterChain {

    public JakartaFilterChain(Iterator iterator, Servlet last, ServletDocker docker) {
        super(iterator, last, docker);
    }

    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {

        try {
            super.doFilter(request, response);
        } catch (ServletExceptionDuck e) {
            throw (ServletException) e.getServletException();
        }
    }
}

