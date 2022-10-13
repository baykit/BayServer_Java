package baykit.bayserver.protocol;

import baykit.bayserver.util.StringUtil;

import java.io.IOException;

public class PacketPartAccessor {

    final Packet packet;
    final int start;
    final int maxLen;
    public int pos;

    public PacketPartAccessor(Packet pkt, int start, int maxLen) {
        this.packet = pkt;
        this.start = start;
        this.maxLen = maxLen;
        this.pos = 0;
    }

    public void putByte(int b) throws IOException {
        putBytes(new byte[]{(byte) b}, 0, 1);
    }

    public void putBytes(byte[] buf) throws IOException {
        putBytes(buf, 0, buf.length);
    }

    public void putBytes(byte[] buf, int ofs, int len) throws IOException {
        if(len > 0) {
            checkWrite(len);
            while(start + pos + len > packet.buf.length) {
                packet.expand();
            }
            System.arraycopy(buf, ofs, packet.buf, start + pos,  len);
            forward(len);
        }
    }

    public void putShort(int val) throws IOException {
        byte h = (byte)(val >> 8 & 0xFF);
        byte l = (byte)(val & 0xFF);
        putBytes(new byte[]{h, l});
    }

    public void putInt(int val) throws IOException {
        byte b1 = (byte)(val >> 24 & 0xFF);
        byte b2 = (byte)(val >> 16 & 0xFF);
        byte b3 = (byte)(val >> 8 & 0xFF);
        byte b4 = (byte)(val & 0xFF);
        putBytes(new byte[]{b1, b2, b3, b4});
    }

    public void putString(String s) throws IOException {
        if (s == null)
            throw new NullPointerException();
        putBytes(StringUtil.toBytes(s));
    }

    public int getByte() throws IOException {
        byte[] buf = new byte[1];
        getBytes(buf);
        return buf[0] & 0xFF;
    }

    public void getBytes(byte[] buf) throws IOException {
        getBytes(buf, 0, buf.length);
    }

    public void getBytes(byte[] buf, int ofs, int len) throws IOException {
        if(buf == null)
            throw new NullPointerException();
        checkRead(len);
        System.arraycopy(packet.buf, start + pos, buf, ofs, len);
        pos += len;
    }

    public int getShort() throws IOException {
        int h = getByte();
        int l = getByte();
        return h << 8 | l;
    }

    public int getInt() throws IOException {
        int b1 = getByte();
        int b2 = getByte();
        int b3 = getByte();
        int b4 = getByte();
        return b1 << 24 | b2 << 16 | b3 << 8 | b4;
    }

    public void checkRead(int len) {
        if (maxLen > 0 && pos + len > maxLen)
            throw new ArrayIndexOutOfBoundsException();
    }

    public void checkWrite(int len) {
        if (maxLen > 0 && pos + len > maxLen)
            throw new ArrayIndexOutOfBoundsException("Buffer overflow");
    }

    public void forward(int len) {
        pos += len;
        if(start + pos > packet.bufLen)
            packet.bufLen = start + pos;
    }
}
