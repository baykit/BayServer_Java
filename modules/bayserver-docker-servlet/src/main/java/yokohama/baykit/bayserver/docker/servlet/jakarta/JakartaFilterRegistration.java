package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.duck.DispatcherTypeDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletContextDuck;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;

import java.util.EnumSet;

public class JakartaFilterRegistration extends FilterRegistrationDuck implements FilterRegistration.Dynamic {


    public JakartaFilterRegistration(String name, String className, ServletContextDuck ctx) {
        super(name, className, ctx);
    }

    public JakartaFilterRegistration(String name, Object filter, ServletContextDuck ctx) {
        super(name, filter, ctx);
    }

    @Override
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {
        addMappingForServletNamesDuck(convertDispatcherTypes(dispatcherTypes), isMatchAfter, servletNames);
    }

    @Override
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {
        addMappingForUrlPatternsDuck(convertDispatcherTypes(dispatcherTypes), isMatchAfter, urlPatterns);
    }

    public static EnumSet<DispatcherTypeDuck> convertDispatcherTypes(EnumSet<DispatcherType> types) {
        EnumSet<DispatcherTypeDuck> res = EnumSet.noneOf(DispatcherTypeDuck.class);
        for(DispatcherType t : types) {
            res.add(convertDispatcherType(t));
        }
        return res;
    }


    public static DispatcherTypeDuck convertDispatcherType(DispatcherType type) {
        switch(type) {
            default:
                throw new IllegalArgumentException();

            case FORWARD:
                return DispatcherTypeDuck.FORWARD;

            case INCLUDE:
                return DispatcherTypeDuck.INCLUDE;

            case REQUEST:
                return DispatcherTypeDuck.REQUEST;

            case ASYNC:
                return DispatcherTypeDuck.ASYNC;

            case ERROR:
                return DispatcherTypeDuck.ERROR;
        }
    }
}