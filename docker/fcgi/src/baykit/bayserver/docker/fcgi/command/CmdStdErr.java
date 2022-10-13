package baykit.bayserver.docker.fcgi.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.fcgi.FcgCommandHandler;
import baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * StdErr command format
 *   raw data
 */
public class CmdStdErr extends InOutCommandBase {

    public CmdStdErr(int reqId) {
        super(FcgType.Stderr, reqId);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleStdErr(this);
    }
}
