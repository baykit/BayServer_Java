package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.Pipe;

public class CommandReceiver {
    GrandAgent agent;
    public Pipe.SourceChannel comRecvChannel;
    Pipe.SinkChannel comSendChannel;
    public boolean closed = false;

    public CommandReceiver(GrandAgent agent, Pipe.SourceChannel comRecvChannel, Pipe.SinkChannel comSendChannel) {
        this.agent = agent;
        this.comRecvChannel = comRecvChannel;
        this.comSendChannel = comSendChannel;
    }

    @Override
    public String toString() {
        return "ComReceiver#" + agent.agentId;
    }

    public void onPipeReadable() {
        try {
            int cmd = IOUtil.readInt32(comRecvChannel);

            BayLog.debug("%s receive command %d pipe=%s", agent, cmd, comRecvChannel);
            switch (cmd) {
                case GrandAgent.CMD_RELOAD_CERT:
                    agent.reloadCert();
                    break;
                case GrandAgent.CMD_MEM_USAGE:
                    agent.printUsage();
                    break;
                case GrandAgent.CMD_SHUTDOWN:
                    agent.shutdown();
                    break;
                case GrandAgent.CMD_ABORT:
                    IOUtil.writeInt32(comSendChannel, GrandAgent.CMD_OK);
                    agent.abort();
                    return;
                default:
                    BayLog.error("Unknown command: %d", cmd);
            }

            IOUtil.writeInt32(comSendChannel, GrandAgent.CMD_OK);
        } catch (IOException e) {
            BayLog.error(e, "%s Command thread aborted(end)", agent);
            close();
        } finally {
            BayLog.debug("%s Command ended", this);
        }
    }

    public void end() {
        BayLog.debug("%s end", this);
        try {
            IOUtil.writeInt32(comSendChannel, GrandAgent.CMD_CLOSE);
        } catch (IOException e) {
            BayLog.error(e);
        }
        close();
    }

    public void close() {
        try {
            comRecvChannel.close();
        }
        catch (IOException e) {
            BayLog.fatal(e);
        }
        try {
            comSendChannel.close();
        }
        catch (IOException e) {
            BayLog.fatal(e);
        }
        closed = true;
    }
}
