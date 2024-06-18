package yokohama.baykit.bayserver.docker.h3.command;

import yokohama.baykit.bayserver.docker.h3.QicCommand;
import yokohama.baykit.bayserver.docker.h3.QicCommandType;

public class CmdData extends QicCommand {

    public final long stmId;

    public CmdData(long stmId) {
        super(QicCommandType.Data);
        this.stmId = stmId;
    }
}
