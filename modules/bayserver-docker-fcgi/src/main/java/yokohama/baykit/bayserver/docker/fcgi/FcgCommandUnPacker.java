package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.CommandStore;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.fcgi.command.*;
import yokohama.baykit.bayserver.docker.fcgi.command.*;

import java.io.IOException;

/**
 *
 * Fast CGI response rule
 *
 *   (StdOut | StdErr)* EndRequest
 *
 */
public class FcgCommandUnPacker extends CommandUnPacker<FcgPacket> {

    FcgCommandHandler handler;
    CommandStore<FcgCommand> cmdStore;

    public FcgCommandUnPacker(FcgCommandHandler handler, CommandStore<FcgCommand> cmdStore) {
        this.handler = handler;
        this.cmdStore = cmdStore;
        reset();
    }

    @Override
    public NextSocketAction packetReceived(FcgPacket pkt) throws IOException {

        FcgCommand cmd = cmdStore.rent(pkt.type());
        cmd.init(pkt.reqId);

        cmd.unpack(pkt);
        NextSocketAction res = cmd.handle(handler);
        cmdStore.Return(cmd);
        return res;
    }

    @Override
    public void reset() {

    }
}
