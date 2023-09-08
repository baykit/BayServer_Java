package yokohama.baykit.bayserver.docker.servlet.duck;


import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * ServletRegistration Implementation for duck typing
 */
public abstract class ServletRegistrationDuck extends RegistrationDuck {

    public int loadOnStartup;
    public Object multipartConfig;
    public Object constraint;
    public String role;
    public int multiPartThreshold;
    public long multiPartMaxSize, multiPartMaxReqSize;
    public String multiPartLocation;

    ServletContextDuck ctx;
    Object servlet;
    boolean initialized;

    public ServletRegistrationDuck(String name, String className, ServletContextDuck ctx) {
        super(name, className);
        this.ctx = ctx;
    }

    public ServletRegistrationDuck(String name, Object servlet, ServletContextDuck ctx) {
        this(name, servlet.getClass().getName(), ctx);
        this.servlet = servlet;
    }

    /////////////////////////////////////////
    // implement ServletRegistration
    /////////////////////////////////////////
    public Set<String> addMapping(String... urlPatterns) {
        Set<String> failSet = new HashSet<>();
        for(String ptn : urlPatterns) {
            if(ctx.docker.servletStore.hasPattern(name, ptn))
                failSet.add(ptn);
            else
                ctx.docker.servletStore.addMapping(name, ptn);
        }
        return failSet;
    }

    public Collection<String> getMappings() {
        throw new Error();
    }

    public String getRunAsRole() {
        throw new Error();
    }


    /////////////////////////////////////////
    // implement ServletRegistration.Dynamic
    /////////////////////////////////////////
    public void setLoadOnStartup(int loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
    }

    public Set<String> setServletSecurityObject(Object constraint) {
        throw new Error();
    }

    public void setMultipartConfigObject(int threshold, long maxSize, long maxReqSize, String location) {
        this.multiPartThreshold = threshold;
        this.multiPartMaxSize = maxSize;
        this.multiPartMaxReqSize = maxReqSize;
        this.multiPartLocation = location;
    }

    public void setRunAsRole(String roleName) {
        this.role = roleName;
    }

    /////////////////////////////////////////
    // Custom methods
    /////////////////////////////////////////
    public Object getServlet()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, ServletExceptionDuck {
        if(servlet == null) {
            servlet = ctx.getClassLoader().loadClass(className).newInstance();
        }

        if(!initialized) {
            ctx.docker.servletHelper.initServlet(servlet, ctx.docker.duckFactory.newServletConfig(ctx, getInitParameters(), getName()));
            initialized = true;
        }
        return servlet;
    }

}
