package baykit.bayserver.docker.fcgi.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.fcgi.FcgCommandHandler;
import baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * StdIn command format
 *   raw data
 */
public class CmdStdIn extends InOutCommandBase {

    public CmdStdIn(int reqId) {
        super(FcgType.Stdin, reqId);
    }

    public CmdStdIn(int reqId, byte[] data, int start, int len) {
        super(FcgType.Stdin, reqId, data, start, len);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleStdIn(this);
    }
}
