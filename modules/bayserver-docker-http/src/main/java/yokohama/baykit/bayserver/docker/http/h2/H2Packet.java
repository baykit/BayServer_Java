package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.Packet;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 * Http2 spec
 *   https://www.rfc-editor.org/rfc/rfc7540.txt
 *   
 * Http2 Frame format
 * +-----------------------------------------------+
 * |                 Length (24)                   |
 * +---------------+---------------+---------------+
 * |   Type (8)    |   Flags (8)   |
 * +-+-+-----------+---------------+-------------------------------+
 * |R|                 Stream Identifier (31)                      |
 * +=+=============================================================+
 * |                   Frame Payload (0...)                      ...
 * +---------------------------------------------------------------+
 */
public class H2Packet extends Packet<H2Type> {
    
    public static final int MAX_PAYLOAD_MAXLEN = 0x00FFFFFF; // = 2^24-1 = 16777215 = 16MB-1
    public static final int DEFAULT_PAYLOAD_MAXLEN = 0x00004000; // = 2^14 = 16384 = 16KB
    public static final int FRAME_HEADER_LEN = 9;

    public H2Flags flags;
    public int streamId = -1;

    public H2Packet(H2Type type) {
        super(type, FRAME_HEADER_LEN, DEFAULT_PAYLOAD_MAXLEN);
    }

    @Override
    public void reset() {
        flags = new H2Flags();
        streamId = -1;
        super.reset();
    }

    @Override
    public String toString() {
        return "H2Packet(" + type.name() + ") hlen=" + headerLen + " dlen=" + dataLen() + " stm=" + streamId + " flg=" + flags;
    }

    public void packHeader() throws IOException {
        PacketPartAccessor acc = newHeaderAccessor();
        putInt24(acc, dataLen());
        acc.putByte(type.no);
        acc.putByte(flags.flags);
        acc.putInt(H2Packet.extractInt31(streamId));
    }

    public static int extractInt31(int val) {
        return val & 0x7FFFFFFF;
    }

    public static int extractFlag(int val) {
        return ((val & 0x80000000) >> 31) & 1;
    }

    public static int consolidateFlagAndInt32(int flag, int val) {
        return (flag & 1) << 31 | (val & 0x7FFFFFFF);
    }

    public static int makeStreamDependency32(boolean excluded, int dep) {
        return (excluded ? 1 : 0) << 31 | extractInt31(dep);
    }

    public static void putInt24(PacketPartAccessor acc, int len) throws IOException {
        byte b1 = (byte)((len >> 16) & 0xFF);
        byte b2 = (byte)((len >> 8) & 0xFF);
        byte b3 = (byte)(len & 0xFF);
        acc.putBytes(new byte[]{b1, b2, b3});
    }
}
