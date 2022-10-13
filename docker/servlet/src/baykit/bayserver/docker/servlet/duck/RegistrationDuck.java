package baykit.bayserver.docker.servlet.duck;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements Registration interface
 */
public class RegistrationDuck {

    protected String name;
    protected String className;
    protected HashMap<String, String> params = new HashMap<>();
    protected boolean async;

    public final boolean getAsyncSupported() {
        return async;
    }


    /////////////////////////////////////////
    // implement Registration
    /////////////////////////////////////////


    public RegistrationDuck(String name, String className) {
        this.name = name;
        this.className = className;
    }

    public final String getName() {
        return name;
    }

    public final String getClassName() {
        return className;
    }

    public final boolean setInitParameter(String name, String value) {
        if(params.containsKey(name))
            return false;
        else {
            params.put(name, value);
            return true;
        }
    }

    public final String getInitParameter(String name) {
        return params.get(name);
    }

    public final Set<String> setInitParameters(Map<String, String> initParameters) {
        HashSet<String> failSet = new HashSet<>();
        for(String name : initParameters.keySet()) {
            String value = initParameters.get(name);
            if(!setInitParameter(name, value))
                failSet.add(name);
        }
        return failSet;
    }

    public final Map<String, String> getInitParameters() {
        return params;
    }

    /////////////////////////////////////////
    // implement Registration.Dynamic
    /////////////////////////////////////////
    public final void setAsyncSupported(boolean isAsyncSupported) {
        async = isAsyncSupported;
    }

}
