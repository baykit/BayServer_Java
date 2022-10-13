package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import jakarta.servlet.*;
import baykit.bayserver.docker.servlet.duck.FilterConfigDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;

import java.util.HashMap;

class JakartaFilterConfig extends FilterConfigDuck implements FilterConfig {

    public JakartaFilterConfig(ServletContextDuck ctx, FilterRegistrationDuck reg) {
        super(ctx, reg);
    }

    @Override
    public ServletContext getServletContext() {
        return (ServletContext)getServletContextDuck();
    }
}