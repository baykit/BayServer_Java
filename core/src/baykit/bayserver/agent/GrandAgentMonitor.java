package baykit.bayserver.agent;

import baykit.bayserver.BayLog;
import baykit.bayserver.util.BlockingIOException;
import baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.Pipe;

public class GrandAgentMonitor {

    int agentId;
    boolean anchorable;
    Pipe sendPipe;
    public Pipe recvPipe;

    GrandAgentMonitor(int agentId, boolean anchorable, Pipe sendPipe, Pipe recvPipe) {
        this.agentId = agentId;
        this.anchorable = anchorable;
        this.sendPipe = sendPipe;
        this.recvPipe = recvPipe;
    }

    @Override
    public String toString()
    {
        return "Monitor#" + agentId;
    }

    public void onReadable()
    {
        try {
            while(true) {
                int res = IOUtil.readInt32(recvPipe.source());
                if (res == GrandAgent.CMD_CLOSE) {
                    BayLog.debug("%s read Close", this);
                    GrandAgent.agentAborted(agentId, anchorable);
                }
                else {
                    BayLog.debug("%s read OK: %d", this, res);
                }
            }
        }
        catch(BlockingIOException e) {
            BayLog.debug("%s No data", this);
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    public void shutdown() throws IOException {
        BayLog.debug("%s send shutdown command", this);
        send(GrandAgent.CMD_SHUTDOWN);
    }

    public void abort() {
        BayLog.debug("%s send abort command", this);
        try {
            send(GrandAgent.CMD_ABORT);
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    public void reloadCert() throws IOException {
        BayLog.debug("%s send reload command", this);
        send(GrandAgent.CMD_RELOAD_CERT);
    }

    public void printUsage() throws IOException {
        BayLog.debug("%s send mem_usage command", this);
        send(GrandAgent.CMD_MEM_USAGE);
    }

    public void send(int cmd) throws IOException {
        BayLog.debug("%s send command %s pipe=%s", this, cmd, sendPipe.sink());
        IOUtil.writeInt32(sendPipe.sink(), cmd);
    }

    public void close()
    {
        try {
            sendPipe.source().close();
            sendPipe.sink().close();
            recvPipe.source().close();
            recvPipe.sink().close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }
}
