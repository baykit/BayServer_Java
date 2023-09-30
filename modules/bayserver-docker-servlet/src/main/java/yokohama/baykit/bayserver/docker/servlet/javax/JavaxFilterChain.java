package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterChainDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;

import javax.servlet.*;

import java.io.IOException;
import java.util.Iterator;

class JavaxFilterChain extends FilterChainDuck implements FilterChain {

    public JavaxFilterChain(Iterator iterator, Servlet last, ServletDocker docker) {
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

