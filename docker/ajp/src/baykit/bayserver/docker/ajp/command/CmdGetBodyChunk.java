package baykit.bayserver.docker.ajp.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.ajp.AjpCommand;
import baykit.bayserver.docker.ajp.AjpCommandHandler;
import baykit.bayserver.docker.ajp.AjpPacket;
import baykit.bayserver.docker.ajp.AjpType;

import java.io.IOException;

/**
 * Get Body Chunk format
 *
 * AJP13_GET_BODY_CHUNK :=
 *   prefix_code       6
 *   requested_length  (integer)
 */
public class CmdGetBodyChunk extends AjpCommand {

    public int reqLen;

    public CmdGetBodyChunk() {
        super(AjpType.GetBodyChunk, false);
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.putByte(type.no);
        acc.putShort(reqLen);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleGetBodyChunk(this);
    }
}
