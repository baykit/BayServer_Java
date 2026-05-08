package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.ajp.command.*;
import yokohama.baykit.bayserver.protocol.CommandStore;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;

import java.io.IOException;

public class AjpCommandUnPacker extends CommandUnPacker<AjpPacket> {

    AjpCommandHandler cmdHandler;
    CommandStore<AjpCommand> cmdStore;

    public AjpCommandUnPacker(AjpCommandHandler cmdHandler, CommandStore<AjpCommand> cmdStore) {
        this.cmdHandler = cmdHandler;
        this.cmdStore = cmdStore;
        reset();
    }

    @Override
    public void reset() {
    }

    @Override
    public NextSocketAction packetReceived(AjpPacket pkt) throws IOException {

        BayLog.debug("ajp:  packet received: type=%s datalen=%d", pkt.type(), pkt.dataLen());
        AjpCommand cmd = cmdStore.rent(pkt.type());
        if(pkt.type() == AjpType.SendBodyChunk)
            ((CmdSendBodyChunk)cmd).init(pkt.buf, pkt.headerLen, pkt.dataLen());

        cmd.unpack(pkt);
        NextSocketAction res = cmd.handle(cmdHandler);
        cmdStore.Return(cmd);
        return res;
    }

    public boolean needData() {
        return cmdHandler.needData();
    }
}
