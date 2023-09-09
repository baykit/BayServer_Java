package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.bcf.*;
import yokohama.baykit.bayserver.bcf.*;

import java.util.HashMap;

public class HttpStatus {
    
    public static final int OK = 200;
    public static final int MOVED_PERMANENTLY = 301;
    public static final int MOVED_TEMPORARILY = 302;
    public static final int NOT_MODIFIED = 304;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int UPGRADE_REQUIRED = 426;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION_NOT_SUPPORTED = 505;


    static boolean initialized = false;
    static HashMap<Integer, String> status = new HashMap<>();

    public static String description(int statusCode) {
        String desc = status.get(statusCode);
        if (desc == null) {
            BayLog.error("Status " + statusCode + " is invalid.");
            return "Unknown Status";
        }
        return desc;
    }

    public static void init(String conf) throws ParseException {
        if(initialized)
            return;

        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(conf);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();
        for(BcfObject o : doc.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                status.put(Integer.parseInt(kv.key), kv.value);
            }
        }
        initialized = true;
    }
}