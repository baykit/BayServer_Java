package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.http.h2.*;

import java.io.IOException;

/**
 * HTTP/2 Data payload format
 * 
 * +---------------+
 * |Pad Length? (8)|
 * +---------------+-----------------------------------------------+
 * |                            Data (*)                         ...
 * +---------------------------------------------------------------+
 * |                           Padding (*)                       ...
 * +---------------------------------------------------------------+
 */
public class CmdData extends H2Command {

    /**
     * This class refers external byte array, so this IS NOT mutable
     */
    public int start;
    public int length;
    public byte[] data;

    public CmdData(int streamId, H2Flags flags) {
        this(streamId, flags, null, 0, 0);
    }
    
    public CmdData(int streamId, H2Flags flags, byte[] data, int start, int len) {
        super(H2Type.Data, streamId, flags);
        this.data = data;
        this.start = start;
        this.length = len;
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
        return handler.handleData(this);
    }
}
