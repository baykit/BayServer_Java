package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.http.h2.*;

import java.io.IOException;

/**
 */
public class CmdPing extends H2Command {

    public byte[] opaqueData;

    public CmdPing(int streamId, H2Flags flags, byte[] opaqueData) {
        super(H2Type.Ping, streamId, flags);
        this.opaqueData = new byte[8];
    }

    public CmdPing(int streamId, H2Flags flags) {
        this(streamId, flags, null);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.getBytes(opaqueData, 0, 8);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putBytes(opaqueData);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handlePing(this);
    }
}
