package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.duck.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebServlet;

import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebInitParam;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

public class JakartaAnnotationHelper implements AnnotationHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for HandleTypes annotation
    //////////////////////////////////////////////////////////////
    @Override
    public void onStartup(Object initializer, Set<Class<?>> c, ServletContextDuck ctx) throws ServletExceptionDuck {

        try {
            ServletContainerInitializer sini = (ServletContainerInitializer)initializer;
            sini.onStartup(c, (ServletContext) ctx);
        }
        catch(ServletException e) {
            throw new ServletExceptionDuck(e);
        }
    }

    @Override
    public Class<?>[] getHandlesTypesValues(Annotation ann) {
        return ((HandlesTypes)ann).value();
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for WebServlet annotation
    //////////////////////////////////////////////////////////////

    @Override
    public String getWebServletName(Annotation ann) {
        return ((WebServlet) ann).name();
    }

    @Override
    public boolean getWebServletAsyncSupported(Annotation ann) {
        return ((WebServlet) ann).asyncSupported();
    }

    @Override
    public Object[] getWebServletInitParams(Annotation ann) {
        return ((WebServlet) ann).initParams();
    }

    @Override
    public String[] getWebServletUrlPatterns(Annotation ann) {
        return ((WebServlet) ann).urlPatterns();
    }

    @Override
    public String[] getWebServletValue(Annotation ann) {
        return ((WebServlet) ann).value();
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for WebFilter annotation
    //////////////////////////////////////////////////////////////

    @Override
    public String getWebFilterName(Annotation ann) {
        return ((WebFilter) ann).filterName();
    }

    @Override
    public boolean getWebFilterAsyncSupported(Annotation ann) {
        return ((WebFilter) ann).asyncSupported();
    }

    @Override
    public Object[] getWebFilterInitParams(Annotation ann) {
        return ((WebFilter) ann).initParams();
    }

    @Override
    public String[] getWebFilterUrlPatterns(Annotation ann) {
        return ((WebFilter) ann).urlPatterns();
    }

    @Override
    public String[] getWebFilterValue(Annotation ann) {
        return ((WebFilter) ann).value();
    }

    @Override
    public String[] getWebFilterServletNames(Annotation ann) {
        return ((WebFilter) ann).servletNames();
    }

    @Override
    public EnumSet<DispatcherTypeDuck> getWebFilterDispatcherTypes(Annotation ann) {
        EnumSet<DispatcherTypeDuck> types = EnumSet.noneOf(DispatcherTypeDuck.class);
        for(DispatcherType t : ((WebFilter) ann).dispatcherTypes()){
            types.add(JakartaFilterRegistration.convertDispatcherType(t));
        }
        return types;
    }


    //////////////////////////////////////////////////////////////
    // Helper methods for WebInitParam annotation
    //////////////////////////////////////////////////////////////

    @Override
    public String getWebInitParamName(Object initParam) {
        return ((WebInitParam)initParam).name();
    }

    @Override
    public String getWebInitParamValue(Object initParam) {
        return ((WebInitParam)initParam).value();
    }


    //////////////////////////////////////////////////////////////
    // Helper methods for Multipart config
    //////////////////////////////////////////////////////////////

    @Override
    public int getMultiPartConfigFileSizeThreshold(Object multiPartConfig) {
        return ((MultipartConfig)multiPartConfig).fileSizeThreshold();
    }

    @Override
    public String getMultiPartConfigLocation(Object multiPartConfig) {
        return ((MultipartConfig)multiPartConfig).location();
    }

    @Override
    public long getMultiPartConfigMaxFileSize(Object multiPartConfig) {
        return ((MultipartConfig)multiPartConfig).maxFileSize();
    }

    @Override
    public long getMultiPartConfigMaxRequestSize(Object multiPartConfig) {
        return ((MultipartConfig)multiPartConfig).maxRequestSize();
    }
}
