package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.docker.fcgi.command.*;
import yokohama.baykit.bayserver.protocol.CommandFactory;

public class FcgCommandFactory extends CommandFactory<FcgCommand> {

    @Override
    public FcgCommand createCommand(int type) {
        switch (type) {
            case FcgType.BeginRequest:
                return new CmdBeginRequest();
            case FcgType.EndRequest:
                return new CmdEndRequest();
            case FcgType.Params:
                return new CmdParams();
            case FcgType.Stdin:
                return new CmdStdIn();
            case FcgType.Stdout:
                return new CmdStdOut();
            case FcgType.Stderr:
                return new CmdStdErr();
            default:
                throw new IllegalArgumentException();
        }
    }
}
