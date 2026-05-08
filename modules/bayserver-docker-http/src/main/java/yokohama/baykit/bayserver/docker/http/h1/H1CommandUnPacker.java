package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.protocol.CommandStore;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h1.command.*;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdHeader;

import java.io.IOException;

public class H1CommandUnPacker extends CommandUnPacker<H1Packet> {

    boolean serverMode;
    H1CommandHandler handler;
    CommandStore<H1Command> store;

    public H1CommandUnPacker(H1CommandHandler handler, CommandStore<H1Command> store, boolean svrMode) {
        this.handler = handler;
        this.store = store;
        this.serverMode = svrMode;
        reset();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements CommandUnPacker
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction packetReceived(H1Packet pac) throws IOException {

        BayLog.debug("h1: read packet type=%s length=%d", pac.type(), pac.dataLen());

        H1Command cmd;
        switch(pac.type()) {
            case H1Type.Header:
                cmd = store.rent(H1Type.Header);
                ((CmdHeader)cmd).init(serverMode);
                break;

            case H1Type.Content:
                cmd = store.rent(H1Type.Content);
                break;

            default:
                reset();
                throw new IllegalStateException();
        }

        cmd.unpack(pac);
        NextSocketAction res = cmd.handle(handler);
        store.Return(cmd);
        return res;
    }

    public boolean reqFinished() {
        return handler.reqFinished();
    }
}
