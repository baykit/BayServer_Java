package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 * HTTP/2 Header payload format
 * 
 * +---------------+
 * |Pad Length? (8)|
 * +-+-------------+-----------------------------------------------+
 * |E|                 Stream Dependency? (31)                     |
 * +-+-------------+-----------------------------------------------+
 * |  Weight? (8)  |
 * +-+-------------+-----------------------------------------------+
 * |                   Header Block Fragment (*)                 ...
 * +---------------------------------------------------------------+
 * |                           Padding (*)                       ...
 * +---------------------------------------------------------------+
 */
public class CmdHeaders extends H2Command {

    /**
     * This class refers external byte array, so this IS NOT mutable
     */
    public int start;
    public int length;
    public byte[] data;

    public int padLength;
    public boolean excluded;
    public int streamDependency;
    public int weight;
    
    public CmdHeaders(int streamId, H2Flags flags) {
        super(H2Type.Headers, streamId, flags);
    }

    public CmdHeaders(int streamId) {
        this(streamId, null);
    }



    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);

        PacketPartAccessor acc = pkt.newDataAccessor();

        if(pkt.flags.padded())
            padLength = acc.getByte();
        if(pkt.flags.priority()) {
            int val = acc.getInt();
            excluded = H2Packet.extractFlag(val) == 1;
            streamDependency = H2Packet.extractInt31(val);
            weight = acc.getByte();
        }
        this.data = pkt.buf;
        this.start = pkt.headerLen + acc.pos;
        this.length = pkt.dataLen() - acc.pos;
    }


    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();

        if(flags.padded()) {
            acc.putByte(padLength);
        }
        if(flags.priority()) {
            acc.putInt(H2Packet.makeStreamDependency32(excluded, streamDependency));
            acc.putByte(weight);
        }

        acc.putBytes(data, start, length);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleHeaders(this);
    }

}
