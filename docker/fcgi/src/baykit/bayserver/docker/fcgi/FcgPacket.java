package baykit.bayserver.docker.fcgi;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *   
 * FCGI Packet (Record) format
 *         typedef struct {
 *             unsigned char version;
 *             unsigned char type;
 *             unsigned char requestIdB1;
 *             unsigned char requestIdB0;
 *             unsigned char contentLengthB1;
 *             unsigned char contentLengthB0;
 *             unsigned char paddingLength;
 *             unsigned char reserved;
 *             unsigned char contentData[contentLength];
 *             unsigned char paddingData[paddingLength];
 *         } FCGI_Record;
 */
public class FcgPacket extends baykit.bayserver.protocol.Packet<FcgType> {
    public static final int PREAMBLE_SIZE = 8;

    public static final int VERSION = 1;
    public static final int MAXLEN = 65535;

    static final int FCGI_NULL_REQUEST_ID = 0;
    public int version = VERSION;
    public int reqId;

    FcgPacket(FcgType type) {
        super(type, PREAMBLE_SIZE, MAXLEN);
    }

    @Override
    public void reset() {
        version = VERSION;
        reqId = 0;
        super.reset();
    }

    @Override
    public String toString() {
        return "FcgPacket(" + type.name() + ") id=" + reqId;
    }
}
