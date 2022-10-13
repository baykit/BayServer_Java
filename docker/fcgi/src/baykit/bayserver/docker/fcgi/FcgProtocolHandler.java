package baykit.bayserver.docker.fcgi;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.docker.base.InboundShip;
import baykit.bayserver.protocol.*;
import baykit.bayserver.watercraft.Ship;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.fcgi.command.CmdEndRequest;
import baykit.bayserver.docker.fcgi.command.CmdStdOut;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.util.DataConsumeListener;
import baykit.bayserver.util.Headers;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.HttpUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

