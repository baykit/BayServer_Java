package baykit.bayserver.docker.servlet.javax;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import baykit.bayserver.docker.servlet.duck.ServletConfigDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;

import java.util.HashMap;
import java.util.Map;

class JavaxServletConfig extends ServletConfigDuck implements ServletConfig{

    public JavaxServletConfig(
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