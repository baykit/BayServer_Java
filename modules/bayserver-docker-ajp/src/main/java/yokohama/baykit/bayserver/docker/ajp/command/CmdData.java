package yokohama.baykit.bayserver.docker.ajp.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.ajp.AjpCommand;
import yokohama.baykit.bayserver.docker.ajp.AjpCommandHandler;
import yokohama.baykit.bayserver.docker.ajp.AjpPacket;
import yokohama.baykit.bayserver.docker.ajp.AjpType;

import java.io.IOException;

/**
 * Data command format
 *
 *   raw data
 */
public class CmdData extends AjpCommand {

    public static final int LENGTH_SIZE = 2;
    public static final int MAX_DATA_LEN = AjpPacket.MAX_DATA_LEN - LENGTH_SIZE;

    public int start;
    public int length;
    public byte[] data;

    public CmdData() {
        this(null, 0, 0);
    }

    public CmdData(byte[] data, int start, int length) {
        super(AjpType.Data, true);
        this.start = start;
        this.length = length;
        this.data = data;
    }

    @Override
    public String toString() {
        String s = "Data(s=" + start + " l=" + length + " d=";
        for(byte b : data) {
            s += "0x" + Integer.toHexString(b & 0xff);
        }
        return s;
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        super.unpack(pkt);
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        length = acc.getShort();

        data = pkt.buf;
        start = pkt.headerLen + 2;
        //BayLog.info("%s", this);
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.putShort(length);
        acc.putBytes(data, start, length);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleData(this);
    }
}
