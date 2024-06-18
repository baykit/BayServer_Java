package yokohama.baykit.bayserver.docker.h3.command;

import io.quiche4j.http3.Http3Header;
import yokohama.baykit.bayserver.docker.h3.QicCommand;
import yokohama.baykit.bayserver.docker.h3.QicCommandType;

import java.util.List;

public class CmdHeader extends QicCommand {

    public final long stmId;
    public final List<Http3Header> reqHeaders;
    public final boolean hasBody;

    public CmdHeader(long stmId, List<Http3Header> reqHeaders, boolean hasBody) {
        super(QicCommandType.Headers);
        this.stmId = stmId;
        this.reqHeaders = reqHeaders;
        this.hasBody = hasBody;
    }
}
