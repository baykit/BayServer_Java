package yokohama.baykit.bayserver.docker.h3.command;

import yokohama.baykit.bayserver.docker.h3.QicCommand;
import yokohama.baykit.bayserver.docker.h3.QicCommandType;
import yokohama.baykit.croute.h3.Headers;

import java.util.List;

public class CmdHeader extends QicCommand {

    public final long stmId;
    public final List<Headers.Header> reqHeaders;
    public final boolean hasBody;

    public CmdHeader(long stmId, List<Headers.Header> reqHeaders, boolean hasBody) {
        super(QicCommandType.Headers);
        this.stmId = stmId;
        this.reqHeaders = reqHeaders;
        this.hasBody = hasBody;
    }
}
