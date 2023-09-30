package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.docker.ajp.command.CmdData;
import yokohama.baykit.bayserver.docker.ajp.command.CmdSendBodyChunk;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

/**
 * The class to hold AJP ship (connection)
 */
public abstract class AjpProtocolHandler
        extends ProtocolHandler<AjpCommand, AjpPacket, AjpType>
        implements AjpCommandHandler{

    protected AjpProtocolHandler(
            PacketStore<AjpPacket, AjpType> pktStore,
            boolean svrMdoe) {

        commandUnpacker = new AjpCommandUnPacker(this);
        packetUnpacker = new AjpPacketUnPacker(pktStore, (AjpCommandUnPacker) commandUnpacker);
        packetPacker = new PacketPacker<>();
        commandPacker = new CommandPacker<>(packetPacker, pktStore);
        serverMode = svrMdoe;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements ProtocolHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public String protocol() {
        return AjpDocker.PROTO_NAME;
    }

    @Override
    public int maxReqPacketDataSize() {
        return CmdData.MAX_DATA_LEN;
    }

    @Override
    public int maxResPacketDataSize() {
        return CmdSendBodyChunk.MAX_CHUNKLEN;
    }

}

