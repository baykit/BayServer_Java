package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.ServletExceptionDuck;
import jakarta.servlet.*;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import baykit.bayserver.BayServer;
import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import baykit.bayserver.docker.servlet.duck.ServletRegistrationDuck;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Implementation of ServletContext for jakarta
 * 
 */
class JakartaServletContext extends ServletContextDuck implements ServletContext {
    
    public JakartaServletContext(String name, String ctxPath, String realPath, ServletDocker docker) {
        super(name, ctxPath, realPath, docker);
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////

    @Override
    public ServletContext getContext(String uripath) {
        return (ServletContext)getContextDuck(uripath);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String urlpath) {
        return (RequestDispatcher)getRequestDispatcherDuck(urlpath);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return (RequestDispatcher)getNamedDispatcherDuck(name);
    }

    @Override
    public Servlet getServlet(String name) throws ServletException {
        try {
            return (Servlet)getServletObject(name);
        } catch (ServletExceptionDuck e) {
            throw (ServletException)e.getServletException();
        }
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        Enumeration<Object> svtObjects = getServletObjectss();
        if(svtObjects == null)
            return null;

        Vector<Servlet> servlets = new Vector<>();
        while(svtObjects.hasMoreElements()) {
            Object svtObj = svtObjects.nextElement();
            servlets.add((Servlet)svtObj);
        }
        return servlets.elements();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return (ServletRegistration.Dynamic)addServletAsObject(servletName, className);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return (ServletRegistration.Dynamic)addServletAsObject(servletName, servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return (ServletRegistration.Dynamic)addServletAsObject(servletName, servletClass);
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
        return (ServletRegistration.Dynamic)addServletAsObject(servletName, jspFile);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return (T)createServletObject(clazz);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return (ServletRegistration)getServletRegistrationDuck(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        Map<String, ServletRegistrationDuck> ducks = getServletRegistrationDucks();
        if(ducks == null)
            return null;
        Map<String, JakartaServletRegistration> regs = new HashMap<>();
        for(String name : ducks.keySet()) {
            regs.put(name,  (JakartaServletRegistration)ducks.get(name));
        }
        return regs;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return (FilterRegistration.Dynamic)addFilterDuck(filterName, className);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return (FilterRegistration.Dynamic)addFilterAsObject(filterName, filter);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
        return (FilterRegistration.Dynamic)addFilterDuck(filterName, filterClass);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> filterClass) throws ServletException {
        try {
            return (T)createFilterObject(filterClass);
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchMethodException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return (FilterRegistration)getFilterRegistrationDuck(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        Map<String, FilterRegistrationDuck> ducks = getFilterRegistrationDucks();
        if(ducks == null)
            return null;
        Map<String, JakartaFilterRegistration> regs = new HashMap<>();
        for(String name : ducks.keySet()) {
            regs.put(name,  (JakartaFilterRegistration)ducks.get(name));
        }
        return regs;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return (SessionCookieConfig)getSessionCookieConfigDuck();
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> set) {
        HashSet<Object> newSet = new HashSet<>();
        for(SessionTrackingMode mode : set) {
            newSet.add(mode);
        }
        setSessionTrackingModeObjects(newSet);
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        Set<Object> set = getDefaultSessionTrackingModeObjects();
        if(set == null)
            return null;

        Set<SessionTrackingMode> newSet = new HashSet<>();
        for(Object mode: set) {
            newSet.add((SessionTrackingMode)mode);
        }
        return newSet;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        Set<Object> set = getEffectiveSessionTrackingModeObjects();
        if(set == null)
            return null;

        Set<SessionTrackingMode> newSet = new HashSet<>();
        for(Object mode: set) {
            newSet.add((SessionTrackingMode)mode);
        }
        return newSet;
    }

    @Override
    public <T extends EventListener> void addListener(T t) {
        addListenerObject(t);
    }

    @Override
    public void addListener(Class<? extends EventListener> aClass) {
        addListenerClass(aClass);
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> listenerClass) throws ServletException {
        try {
            return (T)createListenerObject(listenerClass);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return (JspConfigDescriptor)getJspConfigDescriptorDuck();
    }
}