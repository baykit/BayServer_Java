package yokohama.baykit.bayserver.docker.http.h2;

/**
 * HPack
 *   https://datatracker.ietf.org/doc/html/rfc7541
 *
 *
 */
public class HeaderBlock {
    public enum HeaderOp {
        Index,
        OverloadKnownHeader,
        NewHeader,
        KnownHeader,
        UnknownHeader,
        UpdateDynamicTableSize,
    }
    
    public HeaderOp op;
    public int index;
    public String name;
    public String value;
    public int size;

    @Override
    public String toString() {
        return op + " index=" + index + " name=" + name + " value=" + value + " size=" + size;
    }
}
