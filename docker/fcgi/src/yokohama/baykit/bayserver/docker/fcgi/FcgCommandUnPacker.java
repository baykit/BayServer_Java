package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.fcgi.command.*;
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

    public FcgCommandUnPacker(FcgCommandHandler handler) {
        this.handler = handler;
        reset();
    }

    @Override
    public NextSocketAction packetReceived(FcgPacket pkt) throws IOException {

        FcgCommand cmd;
        switch (pkt.type()) {
            case BeginRequest:
                cmd = new CmdBeginRequest(pkt.reqId);
                break;

            case EndRequest:
                cmd = new CmdEndRequest(pkt.reqId);
                break;

            case Params:
                cmd = new CmdParams(pkt.reqId);
                break;

            case Stdin:
                cmd = new CmdStdIn(pkt.reqId);
                break;

            case Stdout:
                cmd = new CmdStdOut(pkt.reqId);
                break;

            case Stderr:
                cmd = new CmdStdErr(pkt.reqId);
                break;

            default:
                throw new IllegalStateException();
        }

        cmd.unpack(pkt);
        return cmd.handle(handler);
    }

    @Override
    public void reset() {

    }
}
