package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.*;


public class H2ProtocolHandler extends ProtocolHandler<H2Command, H2Packet>  {

    public static final int CTL_STREAM_ID = 0;

    public H2ProtocolHandler(
            H2Handler h2Handler,
            PacketUnpacker<H2Packet> packetUnpacker,
            PacketPacker<H2Packet> packetPacker,
            CommandUnPacker<H2Packet> commandUnpacker,
            CommandPacker<H2Command, H2Packet, ?> commandPacker,
            CommandStore<H2Command> cmdStore,
            boolean serverMode) {
        super(packetUnpacker, packetPacker, commandUnpacker, commandPacker, h2Handler, cmdStore, serverMode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements ProtocolHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String protocol() {
        return "h2";
    }

    @Override
    public int maxReqPacketDataSize() {
        return H2Packet.DEFAULT_PAYLOAD_MAXLEN;
    }

    @Override
    public int maxResPacketDataSize() {
        return H2Packet.DEFAULT_PAYLOAD_MAXLEN;
    }
}
