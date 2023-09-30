package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.http.h2.*;

import java.io.IOException;

/**
 * HTTP/2 GoAway payload format
 * 
 * +-+-------------------------------------------------------------+
 * |R|                  Last-Stream-ID (31)                        |
 * +-+-------------------------------------------------------------+
 * |                      Error Code (32)                          |
 * +---------------------------------------------------------------+
 * |                  Additional Debug Data (*)                    |
 * +---------------------------------------------------------------+
 * 
 */
public class CmdGoAway extends H2Command {

    public int lastStreamId;
    public int errorCode;
    public byte[] debugData;

    public CmdGoAway(int streamId, H2Flags flags) {
        super(H2Type.Goaway, streamId, flags);
    }

    public CmdGoAway(int streamId) {
        this(streamId, null);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        int val = acc.getInt();
        lastStreamId = H2Packet.extractInt31(val);
        errorCode = acc.getInt();
        debugData = new byte[pkt.dataLen() - acc.pos];
        acc.getBytes(debugData, 0, debugData.length);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(lastStreamId);
        acc.putInt(errorCode);
        if(debugData != null)
            acc.putBytes(debugData, 0, debugData.length);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleGoAway(this);
    }
}
