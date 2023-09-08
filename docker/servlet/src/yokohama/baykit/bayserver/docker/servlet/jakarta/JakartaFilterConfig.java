package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import jakarta.servlet.*;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterConfigDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;

class JakartaFilterConfig extends FilterConfigDuck implements FilterConfig {

    public JakartaFilterConfig(ServletContextDuck ctx, FilterRegistrationDuck reg) {
        super(ctx, reg);
    }

    @Override
    public ServletContext getServletContext() {
        return (ServletContext)getServletContextDuck();
    }
}