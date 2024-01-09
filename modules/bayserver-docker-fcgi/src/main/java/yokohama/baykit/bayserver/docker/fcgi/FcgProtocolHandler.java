package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

/**
 * The class to hold FCGI ship (connection)
 */
public class FcgProtocolHandler
        extends ProtocolHandler<FcgCommand, FcgPacket, FcgType> {

    public FcgProtocolHandler(
            FcgHandler fcgHandler,
            PacketUnpacker<FcgPacket> packetUnpacker,
            PacketPacker<FcgPacket> packetPacker,
            CommandUnPacker<FcgPacket> commandUnpacker,
            CommandPacker<FcgCommand, FcgPacket, FcgType, ?> commandPacker,
            boolean serverMode) {
        super(packetUnpacker, packetPacker, commandUnpacker, commandPacker, fcgHandler, serverMode);
    }

    @Override
    public void reset() {
        super.reset();
        commandHandler.reset();
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

