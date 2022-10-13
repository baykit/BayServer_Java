package baykit.bayserver.docker.fcgi;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.CommandHandler;
import baykit.bayserver.docker.fcgi.command.*;

import java.io.IOException;

public interface FcgCommandHandler
        extends CommandHandler<FcgCommand> {

    NextSocketAction handleBeginRequest(CmdBeginRequest cmd) throws IOException;

    NextSocketAction handleEndRequest(CmdEndRequest cmd) throws IOException;

    NextSocketAction handleParams(CmdParams cmd) throws IOException;

    NextSocketAction handleStdErr(CmdStdErr cmd) throws IOException;

    NextSocketAction handleStdIn(CmdStdIn cmd) throws IOException;

    NextSocketAction handleStdOut(CmdStdOut cmd) throws IOException;
}
