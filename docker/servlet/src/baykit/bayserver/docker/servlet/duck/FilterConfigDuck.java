package baykit.bayserver.docker.servlet.duck;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * FilterConfig implementation for duck typing
 */
public abstract class FilterConfigDuck {

    protected final ServletContextDuck ctx;

    protected final FilterRegistrationDuck reg;

    public FilterConfigDuck(
            ServletContextDuck ctx,
            FilterRegistrationDuck reg) {
        this.ctx = ctx;
        this.reg = reg;
    }


    public final String getInitParameter(String name) {
        return reg.getInitParameter(name);
    }

    public final Enumeration getInitParameterNames() {
        return Collections.enumeration(reg.getInitParameters().keySet());
    }

    public final ServletContextDuck getServletContextDuck() {
        return ctx;
    }

    public final String getFilterName() {
        return reg.getName();
    }
}
