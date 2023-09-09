package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.docker.servlet.duck.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

public class AnnotationScanner {

    static class ContainerInitializerDesc {
        String className;
        Class<?>[] values;
        ArrayList<String> detectedValues = new ArrayList<>();

        public ContainerInitializerDesc(String className, Class<?>[] values) {
            this.className = className;
            this.values = values;
        }
    }

    ArrayList<ContainerInitializerDesc> containerInitializers = new ArrayList<>();

    ServletDocker docker;

    public AnnotationScanner(ServletDocker docker) {
        this.docker = docker;
    }

    /**
     * Scan annotations of classes
     */
    public void scanAnnotations() {
        URLClassLoader ldr = docker.ctx.newClassLoader();

        /**
         * First, collect container initializers
         */
        for(URL u : ldr.getURLs()) {
            File path = new File(u.getFile());
            if(path.isFile()) {
                // jar file
                try (FileInputStream is = new FileInputStream(path)){
                    JarInputStream jis = new JarInputStream(is);
                    ZipEntry ent;
                    while((ent = jis.getNextEntry()) != null) {
                        String relPath = ent.getName();
                        if(relPath.equals("META-INF/services/javax.servlet.ServletContainerInitializer")) {
                            StringBuilder s = new StringBuilder();
                            BufferedReader br = new BufferedReader(new InputStreamReader(jis));
                            String clsName = null;
                            while(true) {
                                String line = br.readLine();
                                if(line == null)
                                    break;
                                if(line.trim().startsWith("#") || line.trim().equals(""))
                                    continue;

                                clsName = line;
                                break;
                            }

                            try {
                                Class cls = ldr.loadClass(clsName);
                                if(clsName.endsWith("JasperInitializer"))
                                    BayLog.debug("JASPER FOUND!!!: " + clsName);

                                Class<?> [] types = new Class<?>[0];
                                for(Annotation ann : cls.getAnnotations()) {
                                    if(ann.annotationType().getName().endsWith(".servlet.annotation.HandlesTypes")) {
                                        types = docker.annotationHelper.getHandlesTypesValues(ann);
                                        break;
                                    }
                                }
                                ContainerInitializerDesc dsc =
                                        new ContainerInitializerDesc(clsName, types);
                                containerInitializers.add(dsc);
                                BayLog.debug("Detected container initializer: " + clsName);
                            } catch (ClassNotFoundException e) {
                                BayLog.error(e);
                            }
                        }
                    };

                } catch (IOException e) {
                    BayLog.debug(e.toString());
                }

            }
        }

        /** Second, scan annotations */
        for(URL u : ldr.getURLs()) {
            File path = new File(u.getFile());
            if(path.isDirectory()) {
                // maybe "WEB-INF/classes" directory
                parseAnnotationsInDirectory(path, path, ldr, docker.ctx);
            }
            else if(path.isFile()) {
                // maybe jar file
                try (FileInputStream is = new FileInputStream(path)){
                    JarInputStream jis = new JarInputStream(is);
                    ZipEntry ent;
                    while((ent = jis.getNextEntry()) != null) {
                        String relPath = ent.getName();
                        if(isClass(relPath)) {
                            //BayServer.debug(relPath);
                            String name = getClassName(relPath);
                            parseAnnotation(ldr, name, docker.ctx);
                        }
                    };

                } catch (IOException e) {
                    BayLog.debug(e.toString());
                }
            }
        }
    }

    private boolean isClass(String file) {
        return file.endsWith(".class");
    }

    private String getClassName(String file) {
        file = file.substring(0, file.length() - 6);
        return file.replace('/', '.');
    }


    private void parseAnnotationsInDirectory(File dir, File classRoot, ClassLoader ldr, ServletContextDuck ctx) {
        for(File f: dir.listFiles()) {
            if(f.isDirectory()) {
                parseAnnotationsInDirectory(f, classRoot, ldr, ctx);
            }
            else if(isClass(f.getPath())) {
                String relPath = f.getPath().substring(classRoot.getPath().length() + 1);
                String name = getClassName(relPath);
                parseAnnotation(ldr, name, ctx);
            }
        }
    }


    static Pattern svtAnnPtn = Pattern.compile(".*\\.servlet\\.annotation\\.(.*)");
    private void parseAnnotation(ClassLoader ldr, String name, ServletContextDuck ctx) {
        try {
            //BayLog.debug("parseAnnotation: class=" + name);
            Class cls = ldr.loadClass(name);
            for(ContainerInitializerDesc dsc : containerInitializers) {
                for(Class<?> c : dsc.values) {
                    if(c.isAssignableFrom(cls)) {
                        BayLog.debug("Detected value of container initializer " + dsc.className + ": " + cls);
                        dsc.detectedValues.add(cls.getName());
                    }
                }
            }
            for(Annotation ann : cls.getAnnotations()) {
                String type = ann.annotationType().getName();
                Matcher mch = svtAnnPtn.matcher(type);
                if(mch.matches()) {
                    String shortType = mch.group(1);
                    BayLog.info("Annotation found: class=" + name + " ann=" + ann);
                    switch(shortType) {
                        default:
                            BayLog.warn("Annotation not supported: " + shortType);
                            break;

                        case "WebServlet":
                            handleWebServlet(ann, cls);
                            break;

                        case "WebFilter":
                            handleWebFilter(ann, cls);
                            break;

                        case "WebListener":
                            handleWebListener(ann, cls);
                            break;

                        case "MultiPartConfig":
                            handleMultiPartConfig(ann,cls);
                            break;

                        case "HandlesTypes":
                            break;
                    }
                }
            }
        }
        catch(Throwable e) {
            BayLog.trace(e.toString() + ": " + name);
        }
    }

    public void initContext(ServletDocker docker) {
        for(AnnotationScanner.ContainerInitializerDesc dsc: containerInitializers) {
            try {
                HashSet<Class<?>> valueSet = new HashSet<>();
                for(String value: dsc.detectedValues) {
                    try {
                        valueSet.add(docker.ctx.getClassLoader().loadClass(value));
                    }
                    catch(Throwable e) {
                        BayLog.error(e);
                    }
                }

                Object  initializer;
                try {
                    initializer = docker.ctx.getClassLoader().loadClass(dsc.className).getDeclaredConstructor().newInstance();
                } catch (Throwable e) {
                    BayLog.error(e);
                    return;
                }
                docker.annotationHelper.onStartup(initializer, valueSet, docker.ctx);
            }
            catch(ServletExceptionDuck e) {
                BayLog.error(e.getServletException());
            }
        }
    }


    private void handleWebServlet(Annotation ann, Class<?> cls) {
        ServletRegistrationDuck reg =
                docker.ctx.addServletDuck(
                        docker.annotationHelper.getWebServletName(ann),
                        cls.getName());
        Object[] params = docker.annotationHelper.getWebServletInitParams(ann);
        if(params != null) {
            for (Object param : params) {
                reg.setInitParameter(
                        docker.annotationHelper.getWebInitParamName(param),
                        docker.annotationHelper.getWebInitParamValue(param));
            }
        }
        String[] patterns = docker.annotationHelper.getWebServletUrlPatterns(ann);
        if(patterns != null) {
            reg.addMapping(patterns);
        }
        else {
            String[] value = docker.annotationHelper.getWebServletValue(ann);
            if(value != null)
                reg.addMapping(value);
        }
        reg.setAsyncSupported(docker.annotationHelper.getWebServletAsyncSupported(ann));
    }


    private void handleWebFilter(Annotation ann, Class<?> cls) {
        FilterRegistrationDuck reg =
                docker.ctx.addFilterDuck(
                        docker.annotationHelper.getWebFilterName(ann),
                        cls.getName());
        Object[] params = docker.annotationHelper.getWebFilterInitParams(ann);
        if(params != null) {
            for (Object param : params) {
                reg.setInitParameter(
                        docker.annotationHelper.getWebInitParamName(param),
                        docker.annotationHelper.getWebInitParamValue(param));
            }
        }
        String[] patterns = docker.annotationHelper.getWebFilterUrlPatterns(ann);
        EnumSet<DispatcherTypeDuck> types = docker.annotationHelper.getWebFilterDispatcherTypes(ann);
        if(patterns != null) {
            reg.addMappingForUrlPatternsDuck(types, true, patterns);
        }
        else {
            String[] value = docker.annotationHelper.getWebFilterValue(ann);
            if(value != null)
                reg.addMappingForUrlPatternsDuck(types, true, value);
        }
        String[] names = docker.annotationHelper.getWebFilterServletNames(ann);
        if(names != null) {
            reg.addMappingForServletNamesDuck(types, true, names);
        }
        reg.setAsyncSupported(docker.annotationHelper.getWebServletAsyncSupported(ann));
    }

    private void handleWebListener(Annotation ann, Class cls) {
        docker.ctx.addListenerClass(cls);
    }

    private void handleMultiPartConfig(Annotation ann, Class cls) {

    }
}
