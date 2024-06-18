package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.docker.h3.command.CmdData;
import yokohama.baykit.bayserver.docker.h3.command.CmdFinished;
import yokohama.baykit.bayserver.docker.h3.command.CmdHeader;
import yokohama.baykit.bayserver.protocol.CommandHandler;

public interface QicCommandHandler extends CommandHandler<QicCommand> {

    void handleHeaders(CmdHeader cmd);

    void handleData(CmdData cmd);

    void handleFinished(CmdFinished cmd);
}
