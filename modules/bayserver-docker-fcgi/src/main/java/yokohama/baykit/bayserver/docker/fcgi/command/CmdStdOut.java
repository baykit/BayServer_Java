package yokohama.baykit.bayserver.docker.fcgi.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.fcgi.FcgCommandHandler;
import yokohama.baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * StdOut command format
 *   raw data
 */
public class CmdStdOut extends InOutCommandBase {

    public CmdStdOut(int reqId) {
        this(reqId, new byte[0], 0, 0);
    }

    public CmdStdOut(int reqId, byte[] data, int start, int len) {
        super(FcgType.Stdout, reqId, data, start, len);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleStdOut(this);
    }
}
