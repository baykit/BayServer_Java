package baykit.bayserver.docker.servlet;

import baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import org.apache.tomcat.InstanceManager;

import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Arrays;

public class JasperSupport {
    
    static final BigDecimal VERSION_5 = new BigDecimal(5);
    static final BigDecimal VERSION_6 = new BigDecimal(6);
    static final BigDecimal VERSION_7 = new BigDecimal(7);
    static final BigDecimal VERSION_8_5 = new BigDecimal("8.5");
    static final BigDecimal VERSION_9 = new BigDecimal(9);
    static final BigDecimal VERSION_10 = new BigDecimal(10);
    static final BigDecimal availableVersions[] = {
            VERSION_5,
            VERSION_6,
            VERSION_7,
            VERSION_8_5,
            VERSION_9,
            VERSION_10
    };
    
    BigDecimal tomcatVersion;
    
    public JasperSupport(BigDecimal tomcatVersion) {
        boolean matched = false;
        for(BigDecimal ver : Arrays.asList(availableVersions)) {
            if(tomcatVersion.equals(ver)) {
                matched = true;
                break;
            }
        }
        if(!matched) {
            throw new IllegalArgumentException("Jasper version not matched");
        }
        this.tomcatVersion = tomcatVersion;
    }

    static class InstanceManagerImpl implements InstanceManager {

        @Override
        public Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, IllegalArgumentException, NoSuchMethodException, SecurityException {
            return clazz.newInstance();
        }

        @Override
        public Object newInstance(String className) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
            return Class.forName(className).newInstance();
        }

        @Override
        public Object newInstance(String fqcn, ClassLoader classLoader) throws IllegalAccessException, InvocationTargetException, NamingException, InstantiationException, ClassNotFoundException, IllegalArgumentException, NoSuchMethodException, SecurityException {
            return classLoader.loadClass(fqcn).newInstance();
        }

        @Override
        public void newInstance(Object o) throws IllegalAccessException, InvocationTargetException, NamingException {
        }

        @Override
        public void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException {
        }
        
    }
    
    public void contextInit(ServletContextDuck ctx) throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        if(tomcatVersion.equals(VERSION_5)) {
            // Support classpath attribute for Jasper
            String ATTR_SERVLET_CLASSPATH = "org.apache.catalina.jsp_classpath";
            String classpath = System.getProperty("java.class.path");
/*        String bootClasspath = System
                .getProperty(Constants.PROP_BOOT_CLASSPATH);
        if (bootClasspath != null) {
            classpath += File.pathSeparator + bootClasspath;
        }
*/
            // Set attribute (will call attribute listener)
            ctx.setAttribute(ATTR_SERVLET_CLASSPATH, classpath);
        }
        
        if(tomcatVersion.equals(VERSION_7) || 
                tomcatVersion.equals(VERSION_8_5) || 
                tomcatVersion.equals(VERSION_9) ||
                tomcatVersion.equals(VERSION_10) ) {
            Object insManager = new InstanceManagerImpl();
            ctx.setAttribute("org.apache.tomcat.InstanceManager", insManager);
        }
        
        if(tomcatVersion.equals(VERSION_8_5) || 
                tomcatVersion.equals(VERSION_9) ||
                tomcatVersion.equals(VERSION_10)) {
            Class.forName("org.apache.jasper.servlet.JasperInitializer");
        }
    }
}
