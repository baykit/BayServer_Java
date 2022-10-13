package baykit.bayserver.docker.http.h1;

import baykit.bayserver.BayLog;
import baykit.bayserver.protocol.CommandUnPacker;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.http.h1.command.*;

import java.io.IOException;

public class H1CommandUnPacker extends CommandUnPacker<H1Packet> {

    boolean serverMode;
    H1CommandHandler handler;

    public H1CommandUnPacker(H1CommandHandler handler, boolean svrMode) {
        this.handler = handler;
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

        if(BayLog.isDebugMode())
            BayLog.debug("h1: read packet type=" + pac.type() + " length=" + pac.dataLen());

        H1Command cmd;
        switch(pac.type()) {
            case Header:
                cmd = new CmdHeader(serverMode);
                break;

            case Content:
                cmd = new CmdContent();
                break;

            default:
                reset();
                throw new IllegalStateException();
        }

        cmd.unpack(pac);
        return cmd.handle(handler);
    }

    public boolean reqFinished() {
        return handler.reqFinished();
    }
}
