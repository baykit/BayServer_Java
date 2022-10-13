package baykit.bayserver.docker.servlet.duck;


import java.util.*;

/**
 * FilterRegistration Implementation for duck typing
 */
public abstract class FilterRegistrationDuck extends RegistrationDuck{

    ServletContextDuck ctx;
    Object filter;
    boolean initialized;

    public FilterRegistrationDuck(String name, String className, ServletContextDuck ctx) {
        super(name, className);
        this.ctx = ctx;
    }

    public FilterRegistrationDuck(String name, Object filter, ServletContextDuck ctx) {
        this(name, filter.getClass().getName(), ctx);
        this.filter = filter;
    }

    /////////////////////////////////////////
    // implement FilterRegistration
    /////////////////////////////////////////
    public void addMappingForServletNamesDuck(EnumSet<DispatcherTypeDuck> dispatcherTypes, boolean isMatchAfter, String[] servletNames) {
        throw new Error();
    }

    public void addMappingForUrlPatternsDuck(EnumSet<DispatcherTypeDuck> dispatcherTypes, boolean isMatchAfter, String[] urlPatterns) {
        for(String ptn : urlPatterns) {
            ctx.docker.filterStore.addMapping(name, ptn);
        }
    }
    public final Collection<String> getServletNameMappings() {
        throw new Error();
    }

    public final Collection<String> getUrlPatternMappings() {
        throw new Error();
    }


    /////////////////////////////////////////
    // Other methods
    /////////////////////////////////////////
    public Object getFilter()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, ServletExceptionDuck {
        if(filter == null) {
            filter = ctx.getClassLoader().loadClass(className).newInstance();
        }

        if(!initialized) {
            ctx.docker.filterHelper.initFilter(filter, ctx.docker.duckFactory.newFilterConfig(this, ctx));
            initialized = true;
        }
        return filter;
    }
}