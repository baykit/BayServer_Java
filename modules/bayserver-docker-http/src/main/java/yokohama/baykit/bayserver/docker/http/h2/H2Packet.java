package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.docker.http.h2.huffman.HTree;
import yokohama.baykit.bayserver.protocol.Packet;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

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


    public class H2HeaderAccessor extends PacketPartAccessor {

        public H2HeaderAccessor(Packet pkt, int start, int maxLen) {
            super(pkt, start, maxLen);
        }

        public void putInt24(int len) {
            byte b1 = (byte)((len >> 16) & 0xFF);
            byte b2 = (byte)((len >> 8) & 0xFF);
            byte b3 = (byte)(len & 0xFF);
            putBytes(new byte[]{b1, b2, b3}); 
        }
    }

    public class H2DataAccessor extends PacketPartAccessor {

        public H2DataAccessor(Packet pkt, int start, int maxLen) {
            super(pkt, start, maxLen);
        }

        public int getHPackInt(int prefix, int[] head) {
            int maxVal = 0xFF >> (8 - prefix);

            int firstByte = getByte();
            int firstVal = firstByte & maxVal;
            head[0] = firstByte >> prefix;
            if(firstVal != maxVal) {
                return firstVal;
            }
            else {
                return maxVal + getHPackIntRest();
            }
        }

        public int getHPackIntRest() {
            int rest = 0;
            for(int i = 0; ; i++) {
                int data = getByte();
                boolean cont = (data & 0x80) != 0;
                int value = (data & 0x7F);
                rest = rest + (value << (i * 7));
                if(!cont)
                    break;
            }
            return rest;
        }

        public String getHPackString() {
            int isHuffman[] = new int[1];
            int len = getHPackInt(7, isHuffman);
            byte[] data = new byte[len];
            getBytes(data);
            if(isHuffman[0] == 1) {
                // Huffman
            /*
            for(int i = 0; i < data.length; i++) {
                String bits = "00000000" + Integer.toString(data[i] & 0xFF, 2);
                BayServer.debug(bits.substring(bits.length() - 8));
            }
            */
                return HTree.decode(data);
            }
            else {
                // ASCII
                return new String(data);
            }
        }


        public void putHPackInt(int val, int prefix, int head) {
            int maxVal = 0xFF >> (8 - prefix);
            int headVal = (head  << prefix) & 0xFF;
            if(val < maxVal) {
                putByte(val | headVal);
            }
            else {
                putByte(headVal | maxVal);
                putHPackIntRest(val - maxVal);
            }
        }

        private void putHPackIntRest(int val) {
            while(true) {
                int data = val & 0x7F;
                int nextVal = val >> 7;
                if(nextVal == 0) {
                    // data end
                    putByte(data);
                    break;
                }
                else {
                    // data continues
                    putByte(data | 0x80);
                    val = nextVal;
                }
            }
        }

        public void putHPackString(String value, boolean haffman) {
            if(haffman) {
                throw new IllegalArgumentException();
            }
            else {
                putHPackInt(value.length(), 7, 0);
                putBytes(value.getBytes());
            }
        }

    }
    
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

    public void packHeader() {
        H2HeaderAccessor acc = newH2HeaderAccessor();
        acc.putInt24(dataLen());
        acc.putByte(type.no);
        acc.putByte(flags.flags);
        acc.putInt(H2Packet.extractInt31(streamId));
    }

    public H2HeaderAccessor newH2HeaderAccessor() {
        return new H2HeaderAccessor(this, 0, headerLen);
    }

    public H2DataAccessor newH2DataAccessor() {
        return new H2DataAccessor(this, headerLen, -1);
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

}
