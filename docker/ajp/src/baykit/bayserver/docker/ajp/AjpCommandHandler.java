package baykit.bayserver.docker.ajp;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.CommandHandler;
import baykit.bayserver.docker.ajp.command.*;

import java.io.IOException;

public interface AjpCommandHandler extends CommandHandler<AjpCommand> {

    NextSocketAction handleData(CmdData cmd) throws IOException;

    NextSocketAction handleEndResponse(CmdEndResponse cmd) throws IOException;

    NextSocketAction handleForwardRequest(CmdForwardRequest cmd) throws IOException;

    NextSocketAction handleSendBodyChunk(CmdSendBodyChunk cmd) throws IOException;

    NextSocketAction handleSendHeaders(CmdSendHeaders cmd) throws IOException;

    NextSocketAction handleShutdown(CmdShutdown cmd) throws IOException;

    NextSocketAction handleGetBodyChunk(CmdGetBodyChunk cmd) throws IOException;

    boolean needData();
}
