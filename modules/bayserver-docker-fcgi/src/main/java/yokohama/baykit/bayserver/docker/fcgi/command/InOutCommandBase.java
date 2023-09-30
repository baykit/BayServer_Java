package yokohama.baykit.bayserver.docker.fcgi.command;

import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.fcgi.FcgCommand;
import yokohama.baykit.bayserver.docker.fcgi.FcgPacket;
import yokohama.baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * StdIn/StdOut/StdErr command format
 *   raw data
 */
public abstract class InOutCommandBase extends FcgCommand {

    public static int MAX_DATA_LEN = FcgPacket.MAXLEN - FcgPacket.PREAMBLE_SIZE;
    /**
     * This class refers external byte array, so this IS NOT mutable
     */
    public int start;
    public int length;
    public byte[] data;

    public InOutCommandBase(FcgType type, int reqId) {
        super(type, reqId);
    }

    public InOutCommandBase(FcgType type, int reqId, byte[] data, int start, int len) {
        super(type, reqId);
        this.data = data;
        this.start = start;
        this.length = len;
    }

    @Override
    public void unpack(FcgPacket pkt) throws IOException {
        super.unpack(pkt);
        start = pkt.headerLen;
        length = pkt.dataLen();
        data = pkt.buf;
    }

    @Override
    public void pack(FcgPacket pkt) throws IOException {
        if(data != null && data.length > 0) {
            PacketPartAccessor acc = pkt.newDataAccessor();
            acc.putBytes(data, start, length);
        }

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" + new String(data, 0, length) + "}";
    }
}
