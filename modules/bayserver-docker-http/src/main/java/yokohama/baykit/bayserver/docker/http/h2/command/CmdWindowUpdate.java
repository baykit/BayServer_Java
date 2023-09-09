package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.http.h2.*;

import java.io.IOException;

/**
 * HTTP/2 Window Update payload format
 * 
 * +-+-------------------------------------------------------------+
 * |R|              Window Size Increment (31)                     |
 * +-+-------------------------------------------------------------+
 */
public class CmdWindowUpdate extends H2Command {

    public int windowSizeIncrement;

    public CmdWindowUpdate(int streamId, H2Flags flags) {
        super(H2Type.WindowUpdate, streamId, flags);
    }

    public CmdWindowUpdate(int streamId) {
        this(streamId, null);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        int val = acc.getInt();
        windowSizeIncrement = H2Packet.extractInt31(val);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {

        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(H2Packet.consolidateFlagAndInt32(0, windowSizeIncrement));

        BayLog.debug("Pack windowUpdate size=" + windowSizeIncrement);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleWindowUpdate(this);
    }
}
