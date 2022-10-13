package baykit.bayserver.docker.fcgi.command;

import baykit.bayserver.BayLog;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.docker.fcgi.FcgCommand;
import baykit.bayserver.docker.fcgi.FcgCommandHandler;
import baykit.bayserver.docker.fcgi.FcgPacket;
import baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 *
 * Params command format (Name-Value list)
 *
 *         typedef struct {
 *             unsigned char nameLengthB0;  // nameLengthB0  >> 7 == 0
 *             unsigned char valueLengthB0; // valueLengthB0 >> 7 == 0
 *             unsigned char nameData[nameLength];
 *             unsigned char valueData[valueLength];
 *         } FCGI_NameValuePair11;
 *
 *         typedef struct {
 *             unsigned char nameLengthB0;  // nameLengthB0  >> 7 == 0
 *             unsigned char valueLengthB3; // valueLengthB3 >> 7 == 1
 *             unsigned char valueLengthB2;
 *             unsigned char valueLengthB1;
 *             unsigned char valueLengthB0;
 *             unsigned char nameData[nameLength];
 *             unsigned char valueData[valueLength
 *                     ((B3 & 0x7f) << 24) + (B2 << 16) + (B1 << 8) + B0];
 *         } FCGI_NameValuePair14;
 *
 *         typedef struct {
 *             unsigned char nameLengthB3;  // nameLengthB3  >> 7 == 1
 *             unsigned char nameLengthB2;
 *             unsigned char nameLengthB1;
 *             unsigned char nameLengthB0;
 *             unsigned char valueLengthB0; // valueLengthB0 >> 7 == 0
 *             unsigned char nameData[nameLength
 *                     ((B3 & 0x7f) << 24) + (B2 << 16) + (B1 << 8) + B0];
 *             unsigned char valueData[valueLength];
 *         } FCGI_NameValuePair41;
 *
 *         typedef struct {
 *             unsigned char nameLengthB3;  // nameLengthB3  >> 7 == 1
 *             unsigned char nameLengthB2;
 *             unsigned char nameLengthB1;
 *             unsigned char nameLengthB0;
 *             unsigned char valueLengthB3; // valueLengthB3 >> 7 == 1
 *             unsigned char valueLengthB2;
 *             unsigned char valueLengthB1;
 *             unsigned char valueLengthB0;
 *             unsigned char nameData[nameLength
 *                     ((B3 & 0x7f) << 24) + (B2 << 16) + (B1 << 8) + B0];
 *             unsigned char valueData[valueLength
 *                     ((B3 & 0x7f) << 24) + (B2 << 16) + (B1 << 8) + B0];
 *         } FCGI_NameValuePair44;
 *
 */
public class CmdParams extends FcgCommand {

    public ArrayList<String[]> params = new ArrayList<>();

    public CmdParams(int reqId) {
        super(FcgType.Params, reqId);
    }

    @Override
    public void unpack(FcgPacket pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        while(acc.pos < pkt.dataLen()) {
            int nameLen = readLength(acc);
            int valueLen = readLength(acc);
            byte[] data = new byte[nameLen];
            acc.getBytes(data, 0, data.length);
            String name = new String(data);

            data = new byte[valueLen];
            acc.getBytes(data, 0, data.length);
            String value = new String(data);

            BayLog.trace("Params: %s=%s", name, value);
            addParam(name, value);
        }
    }

    @Override
    public void pack(FcgPacket pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        for(String[] nv: params) {
            byte[] name = nv[0].getBytes();
            byte[] value = nv[1].getBytes();
            int nameLen = name.length;
            int valueLen = value.length;

            writeLength(nameLen, acc);
            writeLength(valueLen, acc);

            acc.putBytes(name);
            acc.putBytes(value);
        }

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleParams(this);
    }

    public int readLength(PacketPartAccessor acc) throws IOException {
        int len = acc.getByte();
        if(len >> 7 == 0) {
            return len;
        }
        else {
            int len2 = acc.getByte();
            int len3 = acc.getByte();
            int len4 = acc.getByte();
            return ((len & 0x7f) << 24) | (len2 << 16) | (len3 << 8) | len4;
        }
    }

    public void writeLength(int len, PacketPartAccessor acc) throws IOException {
        if(len  >> 7 == 0) {
            acc.putByte(len);
        }
        else {
            int len1 = (len >> 24 & 0xFF) | 0x80;
            int len2 = len >> 16 & 0xFF;
            int len3 = len >> 8 & 0xFF;
            int len4 = len & 0xFF;
            acc.putBytes(new byte[]{(byte)len1, (byte)len2, (byte)len3, (byte)len4});
        }
    }

    public void addParam(String name, String value) {
        if(name == null)
            throw new NullPointerException();
        if(value == null)
            value = "";
        params.add(new String[]{name, value});
    }

    @Override
    public String toString() {
        String sep = System.lineSeparator();
        StringWriter sw = new StringWriter();
        sw.write("Params{" + sep);
        for(String[] nv: params) {
            sw.write(" " + nv[0] + " " + nv[1] + sep);
        }
        sw.write("}");
        return sw.toString();
    }
}
