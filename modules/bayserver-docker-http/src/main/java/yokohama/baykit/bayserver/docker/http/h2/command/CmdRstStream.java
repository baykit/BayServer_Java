package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 * HTTP/2 RstStream payload format
 *
 +---------------------------------------------------------------+
 |                        Error Code (32)                        |
 +---------------------------------------------------------------+
 * 
 */
public class CmdRstStream extends H2Command {

    public int errorCode;

    public CmdRstStream(int streamId, H2Flags flags) {
        super(H2Type.RstStream, streamId, flags);
    }

    public CmdRstStream(int streamId) {
        this(streamId, null);
    }

    @Override
    public void unpack(H2Packet pkt) {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        errorCode = acc.getInt();
    }

    @Override
    public void pack(H2Packet pkt) {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(errorCode);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleRstStream(this);
    }
}
