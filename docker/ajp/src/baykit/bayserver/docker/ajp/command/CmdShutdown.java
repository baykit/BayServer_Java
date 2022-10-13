package baykit.bayserver.docker.ajp.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.ajp.AjpCommand;
import baykit.bayserver.docker.ajp.AjpCommandHandler;
import baykit.bayserver.docker.ajp.AjpPacket;
import baykit.bayserver.docker.ajp.AjpType;

import java.io.IOException;

/**
 * Shutdown command format
 *
 *   none
 */
public class CmdShutdown extends AjpCommand {

     public CmdShutdown() {
        super(AjpType.Shutdown, true);
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        super.unpack(pkt);
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleShutdown(this);
    }
}
