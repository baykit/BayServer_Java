package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.CommandHandler;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.docker.http.h2.command.*;

import java.io.IOException;

public interface H2CommandHandler extends CommandHandler<H2Command> {

    NextSocketAction handlePreface(CmdPreface cmd) throws IOException;

    NextSocketAction handleData(CmdData cmd) throws IOException;

    NextSocketAction handleHeaders(CmdHeaders cmd) throws IOException;

    NextSocketAction handlePriority(CmdPriority cmd) throws IOException;

    NextSocketAction handleSettings(CmdSettings cmd) throws IOException;

    NextSocketAction handleWindowUpdate(CmdWindowUpdate cmd) throws IOException;

    NextSocketAction handleGoAway(CmdGoAway cmd) throws IOException;

    NextSocketAction handlePing(CmdPing cmd) throws IOException;

    NextSocketAction handleRstStream(CmdRstStream cmd) throws IOException;
}
