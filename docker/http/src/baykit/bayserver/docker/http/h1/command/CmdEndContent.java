package baykit.bayserver.docker.http.h1.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.http.h1.H1Command;
import baykit.bayserver.docker.http.h1.H1CommandHandler;
import baykit.bayserver.docker.http.h1.H1Packet;
import baykit.bayserver.docker.http.h1.H1Type;

import java.io.IOException;

/**
 * Dummy packet (empty packet) to notify contents are sent
 */
public class CmdEndContent extends H1Command {

    public CmdEndContent() {
        super(H1Type.EndContent);
    }

    @Override
    public void unpack(H1Packet pkt) {
    }

    @Override
    public void pack(H1Packet pkt) throws IOException {
    }

    @Override
    public NextSocketAction handle(H1CommandHandler handler) throws IOException {
        return handler.handleEndContent(this);
    }
}
