package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 * HTTP/2 Priority payload format
 * 
 * +-+-------------------------------------------------------------+
 * |E|                  Stream Dependency (31)                     |
 * +-+-------------+-----------------------------------------------+
 * |   Weight (8)  |
 * +-+-------------+
 * 
 */
public class CmdPriority extends H2Command {

    public int weight;
    public boolean excluded;
    public int streamDependency;

    public CmdPriority(int streamId, H2Flags flags) {
        super(H2Type.Priority, streamId, flags);
    }

    @Override
    public void unpack(H2Packet pkt) {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();

        int val = acc.getInt();
        excluded = H2Packet.extractFlag(val) == 1;
        streamDependency = H2Packet.extractInt31(val);

        weight = acc.getByte();
    }

    @Override
    public void pack(H2Packet pkt) {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(H2Packet.makeStreamDependency32(excluded, streamDependency));
        acc.putByte(weight);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handlePriority(this);
    }
}
