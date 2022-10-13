package baykit.bayserver.docker.ajp;

import baykit.bayserver.BayLog;
import baykit.bayserver.protocol.*;
import baykit.bayserver.docker.base.InboundShip;
import baykit.bayserver.watercraft.Ship;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.ajp.command.CmdData;
import baykit.bayserver.docker.ajp.command.CmdEndResponse;
import baykit.bayserver.docker.ajp.command.CmdSendBodyChunk;
import baykit.bayserver.docker.ajp.command.CmdSendHeaders;
import baykit.bayserver.util.DataConsumeListener;
import baykit.bayserver.util.HttpStatus;

import java.io.IOException;

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

