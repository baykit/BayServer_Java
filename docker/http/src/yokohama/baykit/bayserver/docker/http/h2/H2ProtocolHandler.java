package yokohama.baykit.bayserver.docker.http.h2;

import baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.protocol.CommandPacker;
import yokohama.baykit.bayserver.protocol.PacketPacker;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;


public abstract class H2ProtocolHandler extends ProtocolHandler<H2Command, H2Packet, H2Type> implements H2CommandHandler {

    public static final int CTL_STREAM_ID = 0;

    public final HeaderTable reqHeaderTbl = HeaderTable.createDynamicTable();
    public final HeaderTable resHeaderTbl = HeaderTable.createDynamicTable();

    public H2ProtocolHandler(
            PacketStore<H2Packet, H2Type> pktStore,
            boolean svrMode) {
        commandUnpacker = new H2CommandUnPacker(this);
        packetUnpacker = new H2PacketUnPacker((H2CommandUnPacker) commandUnpacker, pktStore, svrMode);
        packetPacker = new PacketPacker<>();
        commandPacker = new CommandPacker<>(packetPacker, pktStore);
        serverMode = svrMode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // implements ProtocolHandler
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public int maxReqPacketDataSize() {
        return H2Packet.DEFAULT_PAYLOAD_MAXLEN;
    }

    @Override
    public int maxResPacketDataSize() {
        return H2Packet.DEFAULT_PAYLOAD_MAXLEN;
    }

    @Override
    public String protocol() {
        return "h2";
    }
}
