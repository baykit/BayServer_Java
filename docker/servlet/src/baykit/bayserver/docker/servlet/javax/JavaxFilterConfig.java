package baykit.bayserver.docker.servlet.javax;

import baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import javax.servlet.*;
import baykit.bayserver.docker.servlet.duck.FilterConfigDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;

import java.util.HashMap;

class JavaxFilterConfig extends FilterConfigDuck implements FilterConfig {

    public JavaxFilterConfig(ServletContextDuck ctx, FilterRegistrationDuck reg) {
        super(ctx, reg);
    }

    @Override
    public ServletContext getServletContext() {
        return (ServletContext)getServletContextDuck();
    }
}