package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.CommandHandler;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdEndContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdHeader;

import java.io.IOException;

public interface H1CommandHandler extends CommandHandler<H1Command> {

    NextSocketAction handleHeader(CmdHeader cmd) throws IOException;

    NextSocketAction handleContent(CmdContent cmd) throws IOException;

    NextSocketAction handleEndContent(CmdEndContent cmdEndContent);

    boolean reqFinished();
}
