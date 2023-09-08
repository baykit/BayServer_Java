package yokohama.baykit.bayserver.docker.http.h1;

import baykit.bayserver.*;
import baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.docker.http.HtpDocker;
import baykit.bayserver.util.*;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

public abstract class H1ProtocolHandler extends ProtocolHandler<H1Command, H1Packet, H1Type> implements H1CommandHandler{

    boolean keeping;

    protected H1ProtocolHandler(
            PacketStore<H1Packet, H1Type> pktStore,
            boolean svrMode) {
        commandUnpacker = new H1CommandUnPacker(this, svrMode);
        packetUnpacker = new H1PacketUnpacker((H1CommandUnPacker) commandUnpacker, pktStore);
        packetPacker = new PacketPacker<>();
        commandPacker = new CommandPacker<>(packetPacker, pktStore);
        serverMode = svrMode;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        keeping = false;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements ProtocolHandler
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public String protocol() {
        return HtpDocker.H1_PROTO_NAME;
    }


    @Override
    public int maxReqPacketDataSize() {
        return H1Packet.MAX_DATA_LEN;
    }

    @Override
    public int maxResPacketDataSize() {
        return H1Packet.MAX_DATA_LEN;
    }

}
