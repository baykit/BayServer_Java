package yokohama.baykit.bayserver.docker.h3.command;

import yokohama.baykit.bayserver.docker.h3.QicCommand;
import yokohama.baykit.bayserver.docker.h3.QicCommandType;

public class CmdFinished extends QicCommand {

    public final long stmId;

    public CmdFinished(long stmId) {
        super(QicCommandType.Finished);
        this.stmId = stmId;
    }
}
