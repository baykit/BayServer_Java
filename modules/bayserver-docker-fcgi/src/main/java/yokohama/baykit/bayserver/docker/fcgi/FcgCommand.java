package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.Command;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

public abstract class FcgCommand extends Command<FcgCommand, FcgPacket, FcgType, FcgCommandHandler> {

    public int reqId;

    public FcgCommand(FcgType type, int reqId) {
        super(type);
        this.reqId = reqId;
    }

    public void unpack(FcgPacket pkt) throws IOException {
        this.reqId = pkt.reqId;
    }

    /**
     * super class method must be called from last line of override method since header cannot be packed before data is constructed
     */
    public void pack(FcgPacket pkt) throws IOException {
        pkt.reqId = reqId;
        packHeader(pkt);
    }

    void packHeader(FcgPacket pkt) throws IOException {

        PacketPartAccessor acc = pkt.newHeaderAccessor();
        acc.putByte(pkt.version);
        acc.putByte(pkt.type().no);
        acc.putShort(pkt.reqId);
        acc.putShort(pkt.dataLen());
        acc.putByte(0);  // paddinglen
        acc.putByte(0); // reserved
    }
}
