package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.Parameters;

import jakarta.servlet.http.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Request wrapper for RequestDispatcher.include()
 */
class JakartaIncludeRequest extends HttpServletRequestWrapper {

    /** new query string */
    String queryString;
    
    /** new parameters */
    Parameters params;
    
    public JakartaIncludeRequest(HttpServletRequest request, String queryString) {
        super(request);
        this.queryString = queryString;
    }

    void initParams() {         
        if(params == null) {
            params = new Parameters(new HashMap<>(super.getParameterMap()));
            params.parse(queryString, getCharacterEncoding(), true);
        }
    }
    
    @Override
    public String getParameter(String name) {
        String vals[] = getParameterValues(name);
        if(vals != null && vals.length > 0)
            return vals[0];            
        else
            return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        initParams();
        return params.paramMap;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        Map<String, String[]> map = getParameterMap();
        return Collections.enumeration(map.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        Map<String, String[]> map = getParameterMap();
        return map.get(name);
    }
}
