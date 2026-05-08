package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 */
public class CmdPing extends H2Command {

    public byte[] opaqueData;

    public CmdPing() {
        super(H2Type.Ping);
    }

    public void init(int streamId, H2Flags flags, byte[] opaqueData) {
        this.init(streamId, flags);
        this.opaqueData = (opaqueData != null) ? opaqueData : new byte[8];
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
