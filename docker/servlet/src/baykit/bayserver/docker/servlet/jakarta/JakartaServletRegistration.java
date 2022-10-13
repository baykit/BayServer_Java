package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import baykit.bayserver.docker.servlet.duck.ServletRegistrationDuck;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletSecurityElement;

import java.util.Set;

public class JakartaServletRegistration extends ServletRegistrationDuck implements ServletRegistration.Dynamic {


    public JakartaServletRegistration(String name, String className, ServletContextDuck ctx) {
        super(name, className, ctx);
    }

    public JakartaServletRegistration(String name, Object servlet, ServletContextDuck ctx) {
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
