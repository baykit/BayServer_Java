package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletRegistrationDuck;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;

import java.util.Set;

public class JavaxServletRegistration extends ServletRegistrationDuck implements ServletRegistration.Dynamic {


    public JavaxServletRegistration(String name, String className, ServletContextDuck ctx) {
        super(name, className, ctx);
    }

    public JavaxServletRegistration(String name, Object servlet, ServletContextDuck ctx) {
        super(name, servlet, ctx);
    }

    @Override
    public Set<String> setServletSecurity(ServletSecurityElement constraint) {
        return setServletSecurityObject(constraint);
    }

    @Override
    public void setMultipartConfig(MultipartConfigElement multipartConfig) {
        setMultipartConfigObject(
                multipartConfig.getFileSizeThreshold(),
                multipartConfig.getMaxFileSize(),
                multipartConfig.getMaxRequestSize(),
                multipartConfig.getLocation());
    }
}
