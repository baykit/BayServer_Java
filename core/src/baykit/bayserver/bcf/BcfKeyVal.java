package baykit.bayserver.bcf;

public class BcfKeyVal extends BcfObject {
    
    public String key;
    public String value;
    
    public BcfKeyVal(String key, String value, String fileName, int lineNo) {
        super(fileName, lineNo);
        this.key = key;
        this.value = value;
    }
}
