package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.Symbol;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SSLUtil {

    static private Method getAppProtocol, setAppProtocol;
    
    static {
        try {
            getAppProtocol = SSLEngine.class.getMethod("getApplicationProtocol");
            setAppProtocol = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
        } catch (NoSuchMethodException e) {
            // Elder JVM does not have such method
            BayLog.warn(e, BayMessage.get(Symbol.HTP_CANNOT_SUPPORT_HTTP2));
        }
        
    }
    
    public static String getApplicationProtocol(SSLEngine engine) {
        if(getAppProtocol == null)
            return null;
        
        try {
            // bellow code is identical with: 
            //    engine.getApplicationProtocol();
            return (String)getAppProtocol.invoke(engine);
        } catch (IllegalAccessException | InvocationTargetException e) {
            BayLog.error(e);
            return null;
        }
    }
    
    public static void setApplicationProtocols(SSLParameters params, String[] protocols) {
        if(setAppProtocol == null)
            return;

        try {
            // bellow code is identical with: 
            //    params.setApplicationProtocols(protocols);
            setAppProtocol.invoke(params, (Object)protocols); // cast to Object is necessary

        } catch (IllegalAccessException | InvocationTargetException e) {
            BayLog.error(e);
        }

    }
}
