package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;

import java.io.IOException;

/**
 *
 *
 */
public class H2CommandUnPacker extends CommandUnPacker<H2Packet> {

    H2CommandHandler cmdHandler;

    public H2CommandUnPacker(H2CommandHandler cmdHandler) {
        this.cmdHandler = cmdHandler;
        reset();
    }

    @Override
    public void reset() {

    }

    @Override
    public NextSocketAction packetReceived(H2Packet pkt) throws IOException {

        if(BayLog.isDebugMode())
            BayLog.debug("h2: read packet typ=" + pkt.type() + " strmid=" + pkt.streamId + " len=" + pkt.dataLen() + " flgs=" + pkt.flags);

        H2Command cmd;
        switch (pkt.type()) {
            case Preface:
                cmd = new CmdPreface(pkt.streamId, pkt.flags);
                break;

            case Headers:
                cmd = new CmdHeaders(pkt.streamId, pkt.flags);
                break;

            case Priority:
                cmd = new CmdPriority(pkt.streamId, pkt.flags);
                break;

            case Settings:
                cmd = new CmdSettings(pkt.streamId, pkt.flags);
                break;

            case WindowUpdate:
                cmd = new CmdWindowUpdate(pkt.streamId, pkt.flags);
                break;

            case Data:
                cmd = new CmdData(pkt.streamId, pkt.flags);
                break;

            case Goaway:
                cmd = new CmdGoAway(pkt.streamId, pkt.flags);
                break;

            case Ping:
                cmd = new CmdPing(pkt.streamId, pkt.flags);
                break;

            case RstStream:
                cmd = new CmdRstStream(pkt.streamId);
                break;

            case Continuation:
                cmd = new CmdContinuation(pkt.streamId);
                break;

            default:
                throw new IllegalStateException("Received packet: " + pkt);
        }

        cmd.unpack(pkt);
        return cmd.handle(cmdHandler);
    }

    
}
