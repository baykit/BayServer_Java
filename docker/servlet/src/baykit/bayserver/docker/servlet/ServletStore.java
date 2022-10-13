package baykit.bayserver.docker.servlet;

import baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import baykit.bayserver.docker.servlet.duck.ServletRegistrationDuck;

import java.util.ArrayList;

public class ServletStore extends MappingStore {

    ArrayList<ServletRegistrationDuck> servlets = new ArrayList<>();

    public ServletRegistrationDuck getRegistration(String name) {
        for(ServletRegistrationDuck reg: servlets) {
            if(reg.getName().equals(name))
                return reg;
        }
        return null;
    }

    public void addRegistrration(ServletRegistrationDuck reg) {
        servlets.add(reg);
    }



    public MatchResult getServlet(String uri)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, ServletExceptionDuck {
        for(MappingStore.Mapping m : mappings) {
            MappingMatcher.Result r = m.match(uri);
            if(r != null) {
                ServletRegistrationDuck reg = getRegistration(m.name);
                return new MatchResult(
                        reg.getServlet(),
                        r.servletPath,
                        r.pathInfo,
                        reg.getAsyncSupported());
            }
        }
        return null;
    }


}
