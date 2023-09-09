package yokohama.baykit.bayserver.docker.servlet.duck;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.docker.servlet.*;
import yokohama.baykit.bayserver.docker.servlet.MappingStore;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.ServletMessage;
import yokohama.baykit.bayserver.docker.servlet.ServletSymbol;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Implementation of ServletContext
 * 
 */
public abstract class ServletContextDuck {
    
    public static final int MAJOR_VERSION = 3;
    public static final int MINOR_VERSION = 1;
    public static final int EFFECTIVE_MAJOR_VERSION = 2;
    public static final int EFFECTIVE_MINOR_VERSION = 3;

    public Hashtable<String, Object> attrs = new Hashtable<>();
    public String name;
    public String ctxPath;
    public String realPath;
    public ServletDocker docker;
    public String reqEncoding, resEncoding;
    public int timeout;
    public ClassLoader classLoader;
    boolean initialized;

    public ServletContextDuck(String name, String ctxPath, String realPath, ServletDocker docker) {
        this.name = name;
        this.realPath = realPath;
        this.ctxPath = ctxPath;
        this.docker = docker;
        this.classLoader = newClassLoader();
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////
    public final String getContextPath() {
        return ctxPath;
    }

    public final ServletContextDuck getContextDuck(String uripath) {
        return null;
    }

    public final int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public final int getMinorVersion() {
        return MINOR_VERSION;
    }

    public final int getEffectiveMajorVersion() {
        return EFFECTIVE_MAJOR_VERSION;
    }

    public final int getEffectiveMinorVersion() {
        return EFFECTIVE_MINOR_VERSION;
    }

    public final String getMimeType(String file) {
        int pos = file.lastIndexOf('.');
        if (pos >= 0) {
            String ext = file.substring(pos + 1).toLowerCase();
            return Mimes.getType(ext);
        }
        return null;
    }

    public final Set<String> getResourcePaths(String path) {
        String realPath = getRealPath(path);
        HashSet<String> set = new HashSet<>();
        scanResource(realPath, set);
        return set;
    }

    public final URL getResource(String path) throws MalformedURLException {
        int pos = path.indexOf('?');
        if(pos > 0)
            path = path.substring(0, pos);
        File f = new File(getRealPath(path));
        if(!f.exists()) {
            String rpath = path;
            if(rpath.startsWith("/"))
                rpath = rpath.substring(1);

            for(URL url: ((URLClassLoader)getClassLoader()).getURLs()) {
                try {
                    ZipFile jar = new ZipFile(url.getFile());
                    if(jar.getEntry(rpath) != null) {
                        String strJarUrl = "jar:" + url.toExternalForm() + "!/" + rpath;
                        return new URL(strJarUrl);
                    }
                }
                catch(IOException e) {
                    BayLog.trace(e.toString());
                }
            }
            return null;
        }
        else
            return f.toURI().toURL();
    }

    public final InputStream getResourceAsStream(String path) {
        try {
            URL url = getResource(path);
            if(url == null)
                return null;
            
            return url.openStream();
        }
        catch(Exception e) {
            BayLog.error(e);
            return null;
        }
    }

    public final RequestDispatcherDuck getRequestDispatcherDuck(String urlpath) {
        if (!urlpath.startsWith("/"))
            throw new IllegalArgumentException(
                    ServletMessage.get(
                            ServletSymbol.SVT_PATH_MUST_STARTS_WITH_SLASH, urlpath));

        int qpos = urlpath.indexOf('?');
        String qstr, pathInCtxNoQs;
        if(qpos == -1) {
            qstr = null;
            pathInCtxNoQs = urlpath;
        }
        else {
            qstr = urlpath.substring(qpos + 1);
            pathInCtxNoQs = urlpath.substring(0, qpos);
        }

        try {
            MappingStore.MatchResult result = docker.servletStore.getServlet(pathInCtxNoQs);
            if (result == null) {
                // default servlet (file servlet)
                result = new MappingStore.MatchResult(docker.fileSvt, getContextPath(), pathInCtxNoQs, true);
            }

            String ctxPath = docker.ctx.getContextPath();
            String reqUri = ctxPath.endsWith("/") ? ctxPath.substring(0, ctxPath.length() - 1) + urlpath : ctxPath + "/" + urlpath;

            return docker.duckFactory.newRequestDispatcher(
                    result.matchedObj,
                    null,
                    reqUri,
                    result.servletPath,
                    result.pathInfo,
                    qstr);
        }
        catch(Exception e) {
            BayLog.error(e);
            return null;
        }
    }

    public final RequestDispatcherDuck getNamedDispatcherDuck(String name) {

        try {
            ServletRegistrationDuck reg = docker.servletStore.getRegistration(name);
            if(reg == null)
                return null;
            
            Object svt = reg.getServlet();
            if (svt != null) {
                return docker.duckFactory.newNamedDispatcher(svt);
            } else {
                return null;
            }
            
        }
        catch(Exception e) {
            BayLog.error(e);
            return null;
        }

    }

    public final Object getServletObject(String name) throws ServletExceptionDuck {
        // According to spec, this method always returns null
        throw new Error();
    }

    public final Enumeration<Object> getServletObjectss() {
        // According to spec, this method always returns an empty enumeration
        throw new Error();
    }

    public final Enumeration<String> getServletNames() {
        // According to spec, this method always returns an empty enumeration
        throw new Error();
    }

    public final void log(String msg) {
        BayLog.log(BayLog.LOG_LEVEL_INFO, 3, msg);
    }

    public final void log(Exception e, String msg) {
        // This method is deprecated, use log(String message, Throwable throwable) instead. 
        BayLog.log(BayLog.LOG_LEVEL_ERROR, 4, e, msg);
    }

    public final void log(String msg, Throwable throwable) {
        BayLog.log(BayLog.LOG_LEVEL_ERROR, 4, throwable, msg);
    }

    public final String getRealPath(String path) {
        return new File(realPath, path).getPath();
    }

    public final String getServerInfo() {
        return BayServer.getSoftwareName();
    }

    public final String getInitParameter(String name) {
        return docker.ctxParams.get(name);
    }

    public final Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(docker.ctxParams.keySet());
    }

    public final boolean setInitParameter(String name, String value) {
        if(name == null)
            throw new NullPointerException();
        if(docker.ctxParams.containsKey(name))
            return false;

        docker.ctxParams.put(name, value);
        return true;
    }

    public final Object getAttribute(String name) {
        if(BayLog.isDebugMode())
            BayLog.debug(ctxPath + " ctx getAttribute:" + name + "=" + attrs.get(name));
        return attrs.get(name);
    }

    public final Enumeration<String> getAttributeNames() {
        return attrs.keys();
    }

    public final void setAttribute(String name, Object value) {
        if(BayLog.isDebugMode())
            BayLog.debug(ctxPath + " ctx setAttribute:" + name + "=" + value);
        boolean update = attrs.containsKey(name);
        attrs.put(name, value);

        // Invoke event handlers
        ArrayList<EventListener> atrListeners = docker.listenerStore.getListeners(docker.listenerHelper.contextAttributeListenerClass());
        if(!atrListeners.isEmpty()) {
            Object atrEvt = docker.duckFactory.newContextAttributeEvent(docker.ctx, name, value);
            for(Object listener : atrListeners) {
                if(update)
                    docker.listenerHelper.contextAttributeReplaced(listener, atrEvt);
                else
                    docker.listenerHelper.contextAttributeAdded(listener, atrEvt);
            }
        }
    }

    public final void removeAttribute(String name) {
        attrs.remove(name);

        // Invoke event handlers
        ArrayList<EventListener> atrListeners = docker.listenerStore.getListeners(docker.listenerHelper.contextAttributeListenerClass());
        if(!atrListeners.isEmpty()) {
            Object atrEvt = docker.duckFactory.newContextAttributeEvent(docker.ctx, name, null);
            for (Object listener : atrListeners) {
                docker.listenerHelper.contextAttributeRemoved(listener, atrEvt);
            }
        }
    }

    public final String getServletContextName() {
        return name;
    }

    public final ServletRegistrationDuck addServletDuck(String servletName, String className) {
        ServletRegistrationDuck reg = docker.duckFactory.newServletRegistration(servletName, className);
        docker.servletStore.addRegistrration(reg);
        return reg;
    }

    public final ServletRegistrationDuck addServletAsObject(String servletName, Object servlet) {
        ServletRegistrationDuck reg = docker.duckFactory.newServletRegistration(servletName, servlet);
        docker.servletStore.addRegistrration(reg);
        return reg;
    }

    public final ServletRegistrationDuck addServletDuck(String servletName, Class servletClass) {
        return addServletDuck(servletName, servletClass.getName());
    }

    public final ServletRegistrationDuck addJspFileAsObject(String servletName, String jspFile) {
        throw new Error();
    }

    public final Object createServletObject(Class clazz)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return clazz.getDeclaredConstructor().newInstance();
    }

    public final ServletRegistrationDuck getServletRegistrationDuck(String servletName) {
        return docker.servletStore.getRegistration(servletName);
    }

    public final Map<String, ServletRegistrationDuck> getServletRegistrationDucks() {
        Map<String, ServletRegistrationDuck> map = new HashMap<>();
        for(MappingStore.Mapping m : docker.servletStore.mappings) {
            String name = m.name;
            ServletRegistrationDuck r = getServletRegistrationDuck(m.name);
            map.put(name, r);
        }
        return map;
    }


    public final FilterRegistrationDuck addFilterDuck(String filterName, String className) {
        FilterRegistrationDuck reg = docker.duckFactory.newFilterRegistration(filterName, className);
        docker.filterStore.addRegistration(reg);
        return reg;
    }

    public final FilterRegistrationDuck addFilterAsObject(String filterName, Object filter) {
        FilterRegistrationDuck reg = docker.duckFactory.newFilterRegistration(filterName, filter);
        docker.filterStore.addRegistration(reg);
        return reg;
    }

    public final FilterRegistrationDuck addFilterDuck(String filterName, Class filterClass) {
        return addFilterDuck(filterName, filterClass.getName());
    }


    public final <T> T createFilterObject(Class<T> clazz)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return (T)clazz.getDeclaredConstructor().newInstance();
    }

    public final FilterRegistrationDuck getFilterRegistrationDuck(String filterName) {
        return docker.filterStore.getRegistration(filterName);
    }

    public final Map<String, FilterRegistrationDuck> getFilterRegistrationDucks() {
        Map<String, FilterRegistrationDuck> map = new HashMap<>();
        for(MappingStore.Mapping m : docker.servletStore.mappings) {
            String name = m.name;
            FilterRegistrationDuck r = getFilterRegistrationDuck(m.name);
            map.put(name, r);
        }
        return map;
    }

    public final SessionCookieConfigDuck getSessionCookieConfigDuck() {
        throw new Error();
    }

    public final void setSessionTrackingModeObjects(Set<Object> set) {
        throw new Error();
    }

    public final Set<Object> getDefaultSessionTrackingModeObjects() {
        throw new Error();
    }

    public final Set<Object> getEffectiveSessionTrackingModeObjects() {
        throw new Error();
    }

    public final void addListener(String className) {
        docker.listenerStore.addListener(className, null);
    }

    public final <T> void addListenerObject(T t) {
        docker.listenerStore.addListener(t.getClass().getName(), (EventListener) t);
    }

    public final void addListenerClass(Class<?> listenerClass) {
        docker.listenerStore.addListener(listenerClass.getName(), null);
    }

    public final <T> T createListenerObject(Class<T> clazz)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return clazz.getDeclaredConstructor().newInstance();
    }

    public final JspConfigDescriptorDuck getJspConfigDescriptorDuck() {
        return null;
    }

    public final ClassLoader getClassLoader() {
        return classLoader;
    }

    public final void declareRoles(String... strings) {
        throw new Error();
    }

    public final String getVirtualServerName() {
        throw new Error();
    }

    public final int getSessionTimeout() {
        return timeout;
    }

    public final void setSessionTimeout(int sessionTimeout) {
        timeout = sessionTimeout;
    }

    public final String getRequestCharacterEncoding() {
        return reqEncoding != null ? reqEncoding : BayServer.harbor.charset();
    }

    public final void setRequestCharacterEncoding(String encoding) {
        reqEncoding = encoding;
    }

    public final String getResponseCharacterEncoding() {
        return resEncoding != null ? resEncoding : BayServer.harbor.charset();
    }

    public final void setResponseCharacterEncoding(String encoding) {
        resEncoding = encoding;
    }

    ///////////////////////////////////////////////////////////////////
    // custom methods
    ///////////////////////////////////////////////////////////////////

    public URLClassLoader newClassLoader() {
        ArrayList<URL> classPath = new ArrayList<>();

        File classes = new File(docker.town.location(), "WEB-INF/classes");
        File libs = new File(docker.town.location(), "WEB-INF/lib");

        try {
            if (classes.isDirectory())
                classPath.add(classes.toURI().toURL());

            File[] libFiles = libs.listFiles();
            if (libFiles != null) {
                for (File lib : libFiles) {
                    classPath.add(lib.toURI().toURL());
                }
            }
            BayLog.debug("servet: class path: " + classPath);

        } catch (MalformedURLException e) {
            BayLog.error(e);
        }

        return new URLClassLoader(classPath.toArray(new URL[0]), BayServer.class.getClassLoader());
    }

    public void init() {
        if(initialized)
            return;

        docker.annScanner.initContext(docker);

        if(docker.jasperSupport != null) {
            try {
                docker.jasperSupport.contextInit(this);
            }
            catch(Exception e) {
                BayLog.error(e, ServletMessage.get(ServletSymbol.SVT_COULD_NOT_INITIALIZE_JASPER, e.getMessage()));
            }
        }

        setAttribute(docker.ATTR_TEMP_DIR, new File(docker.tempDir));

        // invoke context listeners
        ArrayList<EventListener> ctxListeners = docker.listenerStore.getListeners(docker.listenerHelper.getContextListenerClass());
        if(!ctxListeners.isEmpty()) {
            Object ctxEvt = docker.duckFactory.newServletContextEvent(this);
            for (Object listener : ctxListeners) {
                docker.listenerHelper.contextInitialized(listener, ctxEvt);
            }
        }

        initialized = true;
    }

    public boolean initialized() {
        return initialized;
    }

    ///////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////

    private void scanResource(String path, Set<String> result) {
        File f = new File(path);
        if(f.isFile())
            result.add(f.getPath().substring(realPath.length()).replace(File.separatorChar, '/'));
        else {
            File[] list = f.listFiles();
            if(list != null) {
                for(File c: list) {
                    scanResource(c.getPath(), result);
                }
            }
        }
    }
}