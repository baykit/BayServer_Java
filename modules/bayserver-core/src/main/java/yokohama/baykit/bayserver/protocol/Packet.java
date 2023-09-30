package yokohama.baykit.bayserver.protocol;


import yokohama.baykit.bayserver.util.Reusable;

import java.util.Arrays;

/**
 * Packet format
 *   +---------------------------+
 *   +  Header(type, length etc) +
 *   +---------------------------+
 *   +  Data(payload data)       +
 *   +---------------------------+
 */
public abstract class Packet<T> implements Reusable {

    public static final int INITIAL_BUF_SIZE = 8192 * 4;

    protected final T type;
    public byte[] buf;
    public int bufLen;
    public final int headerLen;
    public final int maxDataLen;

    public Packet(T type, int headerLen, int maxDataLen) {
        this.type = type;
        this.headerLen = headerLen;
        this.maxDataLen = maxDataLen;
        this.buf = new byte[INITIAL_BUF_SIZE];
        this.bufLen = headerLen;
    }

    @Override
    public void reset() {
        // Clear buffer for security
        Arrays.fill(buf, 0, headerLen + dataLen(), (byte) 0); // clear buffer for security
        bufLen = headerLen;
    }

    public final T type() {
        return type;
    }

    public int dataLen() {
        return bufLen - headerLen;
    }

    public void expand() {
        buf = Arrays.copyOf(buf, buf.length * 2);
    }

    public PacketPartAccessor newHeaderAccessor() {
        return new PacketPartAccessor(this, 0, headerLen);
    }

    public PacketPartAccessor newDataAccessor() {
        return new PacketPartAccessor(this, headerLen, -1);
    }

    @Override
    public String toString() {
        return super.toString() + "[type=" + type + "]";
    }
}
