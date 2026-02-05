package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.bcf.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Mimes {

    static Map<String, String> mimeMap = new ConcurrentHashMap<>();

    public static void init(String path) throws ParseException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parseResource(path);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();
        for(BcfObject o : doc.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                mimeMap.put(kv.key.toLowerCase(), kv.value);
            }
        }
    }
    
    public static String getType(String ext) {
        return mimeMap.get(ext.toLowerCase());
    }
}
