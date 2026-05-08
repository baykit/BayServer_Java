package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.docker.http.h1.command.CmdContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdEndContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdHeader;
import yokohama.baykit.bayserver.protocol.CommandFactory;

public class H1CommandFactory extends CommandFactory<H1Command> {

    @Override
    public H1Command createCommand(int type) {
        switch (type) {
            case H1Type.Header:
                return new CmdHeader();
            case H1Type.Content:
                return new CmdContent();
            case H1Type.EndContent:
                return new CmdEndContent();
            default:
                throw new IllegalArgumentException();
        }
    }
}
