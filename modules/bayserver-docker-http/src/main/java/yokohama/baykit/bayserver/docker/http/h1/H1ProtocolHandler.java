package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.docker.http.HtpDocker;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.ship.Ship;

public class H1ProtocolHandler extends ProtocolHandler<H1Command, H1Packet, H1Type> {

    boolean keeping;

    public H1ProtocolHandler(
            H1Handler h1Handler,
            PacketUnpacker<H1Packet> packetUnpacker,
            PacketPacker<H1Packet> packetPacker,
            CommandUnPacker<H1Packet> commandUnpacker,
            CommandPacker<H1Command, H1Packet, H1Type, ?> commandPacker,
            boolean serverMode) {
        super(packetUnpacker, packetPacker, commandUnpacker, commandPacker, h1Handler, serverMode);
    }


    public void init(Ship ship) {
        super.init(ship);
    }


    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        keeping = false;
    }

    /////////////////////////////////////
    // Implements ProtocolHandler
    /////////////////////////////////////

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
