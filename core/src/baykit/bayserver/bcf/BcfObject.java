package baykit.bayserver.bcf;

public abstract class BcfObject {
    public final String fileName;
    public final int lineNo;

    public BcfObject(String fileName, int lineNo) {
        this.fileName = fileName;
        this.lineNo = lineNo;
    }
}
