package baykit.bayserver.docker.servlet;

import baykit.bayserver.BayLog;
import baykit.bayserver.docker.servlet.duck.HttpSessionDuck;

import java.io.IOException;
import java.io.InputStream;

public class ReqInfo implements Cloneable {
    
    public String host;
    public int port;
    public String scheme;
    public String servletPath;
    public String relUri;  // relative path from context path (not include query string)
    private Parameters params;
    public String pathInfo;
    public String pathTranslated;
    public String servletName;
    public String queryString;
    public String reqUri;
    public Object[] cookies;
    public boolean cookieSession;
    public HttpSessionDuck session;
    
    public ReqInfo() {
        
    }
    
    ///////////////////////////////////////////////////////////
    // override methods
    ///////////////////////////////////////////////////////////

    @Override
    public Object clone() throws CloneNotSupportedException {
        ReqInfo ret = (ReqInfo)super.clone();
        ret.params = null;
        return ret;
    }


    ///////////////////////////////////////////////////////////
    // custome methods
    ///////////////////////////////////////////////////////////
    
    public Parameters getParams(String encoding, InputStream in) {
        if(params == null) {
            params = new Parameters();

            if(in != null) {
                try { 
                    params.parse(in, encoding, false);
                } catch (IOException e) {
                    BayLog.error(e);
                }
            }
            
            params.parse(queryString, encoding, false);
        }
        return params;
    }
}
