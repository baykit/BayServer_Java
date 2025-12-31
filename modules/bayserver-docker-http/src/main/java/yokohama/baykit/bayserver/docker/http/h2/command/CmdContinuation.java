package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;
import java.util.ArrayList;

/**
 * HTTP/2 Continuation payload format
 *
 * +---------------------------------------------------------------+
 * |                   Header Block Fragment (*)                 ...
 * +---------------------------------------------------------------+
 */
public class CmdContinuation extends H2Command {

    /**
     * This class refers external byte array, so this IS NOT mutable
     */
    public int start;
    public int length;
    public byte[] data;

    public ArrayList<HeaderBlock> headerBlocks = new ArrayList<>();

    public CmdContinuation(int streamId, H2Flags flags) {
        super(H2Type.Continuation, streamId, flags);
    }

    public CmdContinuation(int streamId) {
        this(streamId, null);
    }



    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        this.data = pkt.buf;
        this.start = pkt.headerLen;
        this.length = pkt.dataLen();
    }


    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        if(flags.padded())
            throw new IllegalStateException("Padding not supported");
        acc.putBytes(data, start, length);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleContinuation(this);
    }
}
