package yokohama.baykit.bayserver.docker.fcgi.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.fcgi.FcgCommandHandler;
import yokohama.baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * StdIn command format
 *   raw data
 */
public class CmdStdIn extends InOutCommandBase {

    public CmdStdIn() {
        super(FcgType.Stdin);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleStdIn(this);
    }
}
