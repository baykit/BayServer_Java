package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.docker.ajp.command.*;

import java.io.IOException;

public class AjpCommandUnPacker extends CommandUnPacker<AjpPacket> {

    AjpCommandHandler cmdHandler;

    public AjpCommandUnPacker(AjpCommandHandler cmdHandler) {
        this.cmdHandler = cmdHandler;
        reset();
    }

    @Override
    public void reset() {
    }

    @Override
    public NextSocketAction packetReceived(AjpPacket pkt) throws IOException {

        BayLog.debug("ajp:  packet received: type=%s datalen=%d", pkt.type(), pkt.dataLen());
        AjpCommand cmd;
        switch (pkt.type()) {
            case Data:
                cmd = new CmdData();
                break;
            case ForwardRequest:
                cmd = new CmdForwardRequest();
                break;
            case SendBodyChunk:
                cmd = new CmdSendBodyChunk(pkt.buf, pkt.headerLen, pkt.dataLen());
                break;
            case SendHeaders:
                cmd = new CmdSendHeaders();
                break;
            case EndResponse:
                cmd = new CmdEndResponse();
                break;
            case Shutdown:
                cmd = new CmdShutdown();
                break;
            case GetBodyChunk:
                cmd = new CmdGetBodyChunk();
                break;
            default:
                throw new Sink();
        }

        cmd.unpack(pkt);
        return cmd.handle(cmdHandler);
    }

    public boolean needData() {
        return cmdHandler.needData();
    }
}
