package baykit.bayserver.docker.http.h2.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.docker.http.h2.*;

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
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        errorCode = acc.getInt();
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(errorCode);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleRstStream(this);
    }
}
