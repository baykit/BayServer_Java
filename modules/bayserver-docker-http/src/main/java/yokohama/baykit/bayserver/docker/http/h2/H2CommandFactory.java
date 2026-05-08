package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.CommandFactory;

public class H2CommandFactory extends CommandFactory<H2Command> {

    @Override
    public H2Command createCommand(int type) {
        switch (type) {
            case H2Type.Data:
                return new CmdData();
            case H2Type.Headers:
                return new CmdHeaders();
            case H2Type.Priority:
                return new CmdPriority();
            case H2Type.RstStream:
                return new CmdRstStream();
            case H2Type.Settings:
                return new CmdSettings();
//            case H2Type.PushPromise:
//                return new CmdPushPromise();
            case H2Type.Ping:
                return new CmdPing();
            case H2Type.GoAway:
                return new CmdGoAway();
            case H2Type.WindowUpdate:
                return new CmdWindowUpdate();
            case H2Type.Continuation:
                return new CmdContinuation();

            default:
                throw new IllegalArgumentException();
        }
    }
}
