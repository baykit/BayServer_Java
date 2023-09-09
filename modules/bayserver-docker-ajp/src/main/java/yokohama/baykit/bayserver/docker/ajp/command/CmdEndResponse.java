package yokohama.baykit.bayserver.docker.ajp.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.ajp.AjpCommand;
import yokohama.baykit.bayserver.docker.ajp.AjpCommandHandler;
import yokohama.baykit.bayserver.docker.ajp.AjpPacket;
import yokohama.baykit.bayserver.docker.ajp.AjpType;

import java.io.IOException;

/**
 * End response body format
 *
 * AJP13_END_RESPONSE :=
 *   prefix_code       5
 *   reuse             (boolean)
 */
public class CmdEndResponse extends AjpCommand {

    public boolean reuse;

    public CmdEndResponse() {
        super(AjpType.EndResponse, false);
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.putByte(type.no);
        acc.putByte(reuse ? 1 : 0);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        super.unpack(pkt);
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.getByte(); // prefix code
        reuse = acc.getByte() != 0;
    }


    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleEndResponse(this);
    }

}
