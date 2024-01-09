package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.docker.ajp.command.CmdData;
import yokohama.baykit.bayserver.docker.ajp.command.CmdSendBodyChunk;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

/**
 * The class to hold AJP ship (connection)
 */
public class AjpProtocolHandler
        extends ProtocolHandler<AjpCommand, AjpPacket, AjpType> {

    public AjpProtocolHandler(
            AjpHandler ajpHandler,
            PacketUnpacker<AjpPacket> packetUnpacker,
            PacketPacker<AjpPacket> packetPacker,
            CommandUnPacker<AjpPacket> commandUnpacker,
            CommandPacker<AjpCommand, AjpPacket, AjpType, ?> commandPacker,
            boolean serverMode) {
        super(packetUnpacker, packetPacker, commandUnpacker, commandPacker, ajpHandler, serverMode);
    }

    @Override
    public void reset() {
        super.reset();
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

