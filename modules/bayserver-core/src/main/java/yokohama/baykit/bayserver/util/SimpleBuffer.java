package yokohama.baykit.bayserver.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class SimpleBuffer implements Reusable {

    public static final int INITIAL_BUFFER_SIZE = 32768;
    private byte[] buf;
    private int len;

    public SimpleBuffer() {
        this(INITIAL_BUFFER_SIZE);
    }

    public SimpleBuffer(int initial) {
        buf = new byte[initial];
        reset();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        // clear for security reason
        Arrays.fill(buf, (byte)0);
        len = 0;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////


    public byte[] bytes() {
        return buf;
    }

    public int length() {
        return len;
    }

    public void put(ByteBuffer buf, int len) {
        while(this.len + len > this.buf.length) {
            extendBuf();
        }
        buf.get(this.buf, this.len, len);
        this.len += len;
    }

    public void put(byte[] bytes, int pos, int len) {
        while(this.len + len > this.buf.length) {
            extendBuf();
        }
        System.arraycopy(bytes, pos, this.buf, this.len, len);
        this.len += len;
    }

    public void put(byte[] bytes) {
        put(bytes, 0, bytes.length);
    }
    
    public void put(byte b) {
        put(new byte[]{b}, 0, 1);
    }

    public void resetPos() {
        len = 0;
    }

    public int indexOf(byte b, int start)
    {
        for(int i = start; i < len; i++) {
            if(buf[i] == b)
                return i;
        }
        return -1;
    }

    private void extendBuf() {
        //BayLog.info("extend: " + buf.length + "->" + buf.length * 2);
        byte[] newBuf = new byte[buf.length * 2];
        System.arraycopy(buf, 0, newBuf, 0, buf.length);
        buf = newBuf;
    }

}
