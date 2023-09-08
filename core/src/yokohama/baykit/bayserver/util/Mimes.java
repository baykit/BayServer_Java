package yokohama.baykit.bayserver.util;

import baykit.bayserver.bcf.*;
import yokohama.baykit.bayserver.bcf.*;

import java.util.HashMap;

public class Mimes {

    static HashMap<String, String> mimeMap = new HashMap<>();

    public static void init(String conf) throws ParseException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(conf);
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
