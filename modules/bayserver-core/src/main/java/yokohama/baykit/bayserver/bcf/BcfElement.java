package yokohama.baykit.bayserver.bcf;

import java.util.ArrayList;

public class BcfElement extends BcfObject {

    public String name;
    public String arg;
    
    public ArrayList<BcfObject> contentList = new ArrayList<>();

    public BcfElement(String name, String arg, String fileName, int lineNo) {
        super(fileName, lineNo);
        this.name = name;
        this.arg = arg;
    }
    
    public String getValue(String key) {
        for(BcfObject o : contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                if(kv.key.equalsIgnoreCase(key))
                    return kv.value;
            }
        }
        return null;
    }
}
