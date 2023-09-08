package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.protocol.CommandUnPacker;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.docker.http.h2.command.*;
import yokohama.baykit.bayserver.docker.http.h2.command.*;

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
            case H2Type.Preface:
                cmd = new CmdPreface(pkt.streamId, pkt.flags);
                break;

            case H2Type.Headers:
                cmd = new CmdHeaders(pkt.streamId, pkt.flags);
                break;

            case H2Type.Priority:
                cmd = new CmdPriority(pkt.streamId, pkt.flags);
                break;

            case H2Type.Settings:
                cmd = new CmdSettings(pkt.streamId, pkt.flags);
                break;

            case H2Type.WindowUpdate:
                cmd = new CmdWindowUpdate(pkt.streamId, pkt.flags);
                break;

            case H2Type.Data:
                cmd = new CmdData(pkt.streamId, pkt.flags);
                break;

            case H2Type.Goaway:
                cmd = new CmdGoAway(pkt.streamId, pkt.flags);
                break;

            case H2Type.Ping:
                cmd = new CmdPing(pkt.streamId, pkt.flags);
                break;

            case H2Type.RstStream:
                cmd = new CmdRstStream(pkt.streamId);
                break;

            default:
                throw new IllegalStateException("Received packet" + pkt);
        }

        cmd.unpack(pkt);
        return cmd.handle(cmdHandler);
    }

    
}
