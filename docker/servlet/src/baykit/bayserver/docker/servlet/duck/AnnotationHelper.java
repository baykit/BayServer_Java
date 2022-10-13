package baykit.bayserver.docker.servlet.duck;

import javax.servlet.annotation.WebInitParam;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.Set;

public interface AnnotationHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for HandleTypes annotation
    //////////////////////////////////////////////////////////////

    void onStartup(Object initializer, Set<Class<?>> c, ServletContextDuck ctx) throws ServletExceptionDuck;

    Class<?>[] getHandlesTypesValues(Annotation ann);


    //////////////////////////////////////////////////////////////
    // Helper methods for WebServlet annotation
    //////////////////////////////////////////////////////////////

    String getWebServletName(Annotation ann);

    boolean getWebServletAsyncSupported(Annotation ann);

    Object[] getWebServletInitParams(Annotation ann);

    String[] getWebServletUrlPatterns(Annotation ann);

    public String[] getWebServletValue(Annotation ann);

    //////////////////////////////////////////////////////////////
    // Helper methods for WebFilter annotation
    //////////////////////////////////////////////////////////////

    String getWebFilterName(Annotation ann);

    boolean getWebFilterAsyncSupported(Annotation ann);

    Object[] getWebFilterInitParams(Annotation ann);

    String[] getWebFilterUrlPatterns(Annotation ann);

    String[] getWebFilterValue(Annotation ann);

    String[] getWebFilterServletNames(Annotation ann);

    EnumSet<DispatcherTypeDuck> getWebFilterDispatcherTypes(Annotation ann);

    //////////////////////////////////////////////////////////////
    // Helper methods for WebInitParam annotation
    //////////////////////////////////////////////////////////////

    String getWebInitParamName(Object initParam);

    String getWebInitParamValue(Object initParam);


    //////////////////////////////////////////////////////////////
    // Helper methods for Multipart config
    //////////////////////////////////////////////////////////////

    int getMultiPartConfigFileSizeThreshold(Object multiPartConfig);

    String getMultiPartConfigLocation(Object multiPartConfig);

    long getMultiPartConfigMaxFileSize(Object multiPartConfig);

    long getMultiPartConfigMaxRequestSize(Object multiPartConfig);
}