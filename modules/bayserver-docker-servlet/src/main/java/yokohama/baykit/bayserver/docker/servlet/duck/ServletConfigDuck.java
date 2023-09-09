package yokohama.baykit.bayserver.docker.servlet.duck;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * ServletConfig implementation for duck typing
 */
public abstract class ServletConfigDuck {

    protected final ServletContextDuck ctx;

    protected  final Map<String, String> params;

    protected  final String name;

    public ServletConfigDuck(
            ServletContextDuck ctx,
            Map<String, String> params,
            String name) {
        this.ctx = ctx;
        this.params = params;
        this.name = name;
    }

    public final String getServletName() {
        return name;
    }

    public final ServletContextDuck getServletContextDuck() {
        return ctx;
    }

    public final String getInitParameter(String name) {
        return params.get(name);
    }

    public final Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(params.keySet());
    }

}
