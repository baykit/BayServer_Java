package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

/**
 * The class to hold FCGI ship (connection)
 */
public abstract class FcgProtocolHandler
        extends ProtocolHandler<FcgCommand, FcgPacket, FcgType>
        implements FcgCommandHandler {

    protected FcgProtocolHandler(
            PacketStore<FcgPacket, FcgType> pktStore,
            boolean svrMode) {
        commandUnpacker = new FcgCommandUnPacker(this);
        packetUnpacker = new FcgPacketUnPacker(pktStore, (FcgCommandUnPacker) commandUnpacker);
        packetPacker = new PacketPacker<>();
        commandPacker = new CommandPacker<>(packetPacker, pktStore);
        serverMode = svrMode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements ProtocolHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public String protocol() {
        return FcgDocker.PROTO_NAME;
    }

    @Override
    public int maxReqPacketDataSize() {
        return FcgPacket.MAXLEN;
    }

    @Override
    public int maxResPacketDataSize() {
        return FcgPacket.MAXLEN;
    }

}

