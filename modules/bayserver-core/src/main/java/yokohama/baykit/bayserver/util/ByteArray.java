package yokohama.baykit.bayserver.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteArray {

    public static final int INITIAL_BUF_SIZE = 8192 * 4;
    
    byte[] buf;
    int pos;
    
    public ByteArray() {
        this(INITIAL_BUF_SIZE);
    }
    
    public ByteArray(int capacity) {
        buf = new byte[capacity];
    }

    public ByteArray(byte[] buf, int length) {
        this.buf = buf;
        this.pos = length;
    }

    public ByteArray(ByteBuffer buf) {
        this.buf = buf.array();
        pos = buf.limit();
    }

    public void clear() {
        Arrays.fill(buf, 0, pos, (byte)0); // clear buffer for security
        pos = 0;
    }
    
    public byte[] buffer() {
        return buf;
    }

    public int length() {
        return pos;
    }

    public void putBytes(byte[] b, int ofs, int len) {
        if(b == null)
            throw new NullPointerException();
        while(pos + len > buf.length) {
            buf = Arrays.copyOf(buf, buf.length * 2);
        }
        System.arraycopy(b, ofs, buf, pos, len);
        pos += len;
    }
}
