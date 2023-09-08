package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;

import java.util.ArrayList;

public class FilterStore extends MappingStore {

    ArrayList<FilterRegistrationDuck> filters = new ArrayList<>();

    public FilterRegistrationDuck getRegistration(String name) {
        for(FilterRegistrationDuck d: filters) {
            if(d.getName().equals(name))
                return d;
        }
        return null;
    }

    public void addRegistration(FilterRegistrationDuck reg) {
        filters.add(reg);
    }

    public ArrayList<MatchResult> getFilter(String uri)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, ServletExceptionDuck {
        ArrayList<MatchResult> res = new ArrayList<>();
        for(MappingStore.Mapping m : mappings) {
            MappingMatcher.Result r = m.match(uri);
            if(r != null) {
                FilterRegistrationDuck reg = getRegistration(m.name);
                res.add(new MatchResult(
                        reg.getFilter(),
                        r.servletPath,
                        r.pathInfo,
                        reg.getAsyncSupported()));
            }
        }
        return res;
    }
}
