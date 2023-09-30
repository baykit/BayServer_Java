package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterChainDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterConfigDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterHelper;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import javax.servlet.*;

import java.io.IOException;
import java.util.Iterator;

public class JavaxFilterHelper implements FilterHelper {

    ServletDocker docker;

    public JavaxFilterHelper(ServletDocker docker) {
        this.docker = docker;
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for Filter
    //////////////////////////////////////////////////////////////

    @Override
    public void initFilter(Object filter, FilterConfigDuck cfg) throws ServletExceptionDuck {
        try {
            if(!(filter instanceof Filter))
                throw new ServletException("Filter not implemented " + Filter.class.getName() + ":" + filter.getClass().getName());
            ((Filter)filter).init((FilterConfig) cfg);
        } catch (ServletException e) {
            throw new ServletExceptionDuck(e);
        }
    }

    @Override
    public void doFilter(Object filter, Object req, Object res, FilterChainDuck chain) throws IOException, ServletExceptionDuck {
        try {
            ((Filter)filter).doFilter((ServletRequest)req, (ServletResponse)res, (FilterChain)chain);
        } catch (ServletException e) {
            throw new ServletExceptionDuck(e);
        }
    }

    @Override
    public FilterChainDuck newFilterChain(Iterator<Object> iterator, Object last) {
        return new JavaxFilterChain(iterator, (Servlet)last, docker);
    }

}
