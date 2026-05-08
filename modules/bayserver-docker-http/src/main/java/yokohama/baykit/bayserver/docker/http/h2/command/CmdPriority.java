package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.protocol.ProtocolException;

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

    public CmdPriority() {
        super(H2Type.Priority);
    }

    ///////////////////////////////////////////////
    // Implements Reusable
    ///////////////////////////////////////////////

    @Override
    public void reset() {

    }

    ///////////////////////////////////////////////
    // Implements Command
    ///////////////////////////////////////////////
    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();

        int val = acc.getInt();
        excluded = H2Packet.extractFlag(val) == 1;
        streamDependency = H2Packet.extractInt31(val);

        weight = acc.getByte();

        // RFC 7540 § 5.3.1: a stream MUST NOT depend on itself.
        if (streamDependency == streamId)
            throw new ProtocolException(
                    "PRIORITY stream depends on itself: " + streamId);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
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
