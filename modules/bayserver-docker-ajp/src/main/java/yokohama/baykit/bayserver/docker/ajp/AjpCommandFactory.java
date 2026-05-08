package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.protocol.CommandFactory;

public class AjpCommandFactory extends CommandFactory<AjpCommand> {

    @Override
    public AjpCommand createCommand(int type) {
        switch (type) {
            case AjpType.Data:
                return new CmdData();
            case AjpType.ForwardRequest:
                return new CmdForwardRequest();
            case AjpType.SendBodyChunk:
                return new CmdSendBodyChunk();
            case AjpType.SendHeaders:
                return new CmdSendHeaders();
            case AjpType.EndResponse:
                return new CmdEndResponse();
            case AjpType.GetBodyChunk:
                return new CmdGetBodyChunk();

            default:
                throw new IllegalArgumentException();
        }
    }
}
