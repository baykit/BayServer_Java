package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.protocol.Packet;

public class H1Packet extends Packet<H1Type> {

    public static final int MAX_HEADER_LEN = 0; // H1 packet does not have packet header
    public static final int MAX_DATA_LEN = 65536;

    /** space */
    public static final byte[] SP_BYTES = " ".getBytes();
    /** Line separator */
    public static byte[] CRLF_BYTES = "\r\n".getBytes();

    H1Packet(H1Type type) {
        super(type, MAX_HEADER_LEN, MAX_DATA_LEN);
    }

    @Override
    public String toString() {
        return "H1Packet(" + type.name() + ") len=" + dataLen();
    }
}
