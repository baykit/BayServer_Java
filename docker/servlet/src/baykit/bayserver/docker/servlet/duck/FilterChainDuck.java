package baykit.bayserver.docker.servlet.duck;

import baykit.bayserver.docker.servlet.ServletDocker;

import java.io.IOException;
import java.util.Iterator;

public abstract class FilterChainDuck {

    /** An iterator pointing to working filter. */
    private Iterator iterator;

    /** The execution to execute finally */
    private Object lastServlet;

    private ServletDocker docker;

    public FilterChainDuck(Iterator iterator, Object lastServlet, ServletDocker docker) {
        this.iterator = iterator;
        this.lastServlet = lastServlet;
        this.docker = docker;
    }

    public void doFilter(Object req, Object res)
        throws IOException, ServletExceptionDuck {

        if (iterator.hasNext()) {
            docker.filterHelper.doFilter(iterator.next(), req, res, this);
        } else {
            docker.servletHelper.service(lastServlet, req, res);
        }
    }
}
