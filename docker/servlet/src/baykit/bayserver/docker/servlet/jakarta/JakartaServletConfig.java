package baykit.bayserver.docker.servlet.jakarta;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import baykit.bayserver.docker.servlet.duck.ServletConfigDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;

import java.util.HashMap;
import java.util.Map;

class JakartaServletConfig extends ServletConfigDuck implements ServletConfig{

    public JakartaServletConfig(
            ServletContextDuck ctx,
            Map<String, String> params,
            String name) {
        super(ctx, params, name);
    }


    @Override
    public ServletContext getServletContext() {
        return (ServletContext)getServletContextDuck();
    }
}