package yokohama.baykit.bayserver;

import yokohama.baykit.bayserver.bcf.*;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.bcf.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class BayDockers {

    public static HashMap<String, Class<Docker>> dockerMap = new HashMap<>();




    public static void init(String path) throws ParseException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parseResource(path);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();
        
        for(BcfObject o : doc.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                try {
                    Class<Docker> cls = (Class<Docker>) Class.forName(kv.value);
                    dockerMap.put(kv.key, cls);
                }
                catch(Exception e) {
                    BayLog.error(
                            e,
                            BayMessage.get(
                                    Symbol.CFG_INVALID_DOCKER_CLASS,
                                    kv.value,
                                    kv.fileName,
                                    kv.lineNo));
                }
            }
        }
    }

    /**
     * Create docker from ini file element
     * @param elm
     * @return
     * @throws BayException
     */
    public static Docker createDocker(BcfElement elm, Docker parent) throws BayException {
        String alias = elm.getValue("docker");
        Docker d = createDocker(elm.name, alias);

        d.init(elm, parent);
        return d;
    }

    private static Docker createDocker(String category, String alias) throws BayException {
        Class<Docker> c;
        String key;
        if(StringUtil.empty(alias)) {
            key = category;
        }
        else {
            key = category + ":" + alias;
        }
        c = dockerMap.get(key);
        if(c == null)
            throw new BayException(BayMessage.get(Symbol.CFG_DOCKER_NOT_FOUND, key));

        try {
            return c.getDeclaredConstructor().newInstance();
        }
        catch(InvocationTargetException e) {
            BayLog.error(e);
            throw new BayException(e.getMessage());
        }
        catch(Exception e) {
            BayLog.error(e);
            throw new BayException(e.getMessage());
        }
    }
}
