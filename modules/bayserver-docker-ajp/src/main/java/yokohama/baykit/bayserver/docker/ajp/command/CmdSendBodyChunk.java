package yokohama.baykit.bayserver.docker.ajp.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.ajp.AjpCommand;
import yokohama.baykit.bayserver.docker.ajp.AjpCommandHandler;
import yokohama.baykit.bayserver.docker.ajp.AjpPacket;
import yokohama.baykit.bayserver.docker.ajp.AjpType;

import java.io.IOException;

/**
 * Send body chunk format
 *
 * AJP13_SEND_BODY_CHUNK :=
 *   prefix_code   3
 *   chunk_length  (integer)
 *   chunk        *(byte)
 */
public class CmdSendBodyChunk extends AjpCommand {
    public byte[] chunk;
    public int offset;
    public int length;

    public static final int MAX_CHUNKLEN = AjpPacket.MAX_DATA_LEN - 4;

    public CmdSendBodyChunk(byte[] buf, int ofs, int len) {
        super(AjpType.SendBodyChunk, false);
        this.chunk = buf;
        this.offset = ofs;
        this.length = len;
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        if(length > MAX_CHUNKLEN)
            throw new IllegalArgumentException();

        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();

        acc.putByte(type.no);
        acc.putShort(length);
        acc.putBytes(chunk, offset, length);
        acc.putByte(0);   // maybe document bug

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.getByte(); // code
        length = acc.getShort();
        if(chunk == null || length > chunk.length)
            chunk = new byte[length];
        acc.getBytes(chunk, 0, length);
    }

    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleSendBodyChunk(this);
    }
}
