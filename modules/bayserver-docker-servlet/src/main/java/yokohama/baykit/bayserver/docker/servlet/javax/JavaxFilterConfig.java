package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import javax.servlet.*;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterConfigDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;

class JavaxFilterConfig extends FilterConfigDuck implements FilterConfig {

    public JavaxFilterConfig(ServletContextDuck ctx, FilterRegistrationDuck reg) {
        super(ctx, reg);
    }

    @Override
    public ServletContext getServletContext() {
        return (ServletContext)getServletContextDuck();
    }
}