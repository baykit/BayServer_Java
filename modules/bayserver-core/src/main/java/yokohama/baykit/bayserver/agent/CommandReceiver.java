package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.MemUsage;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.monitor.GrandAgentMonitor;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CommandReceiver extends Ship {
    GrandAgent agent;
    public Rudder rudder;
    public boolean closed = false;

    public CommandReceiver(GrandAgent agent, Rudder rd) {
        this.agent = agent;
        this.rudder = rd;
    }

    @Override
    public String toString() {
        return "ComReceiver#" + agent.agentId;
    }

    ////////////////////////////////////////////
    // Implements Ship
    ////////////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String pcl) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf) throws IOException {
        int cmd = GrandAgentMonitor.bufferToInt(buf);
        onReadCommand(cmd);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyEof() {
        return null;
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.error(e);
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

    @Override
    public void notifyClose() {

    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return false;
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    public void onReadCommand(int cmd) {
        try {

            BayLog.debug("%s receive command %d rd=%s", agent, cmd, rudder);
            switch (cmd) {
                case GrandAgent.CMD_RELOAD_CERT:
                    agent.reloadCert();
                    break;
                case GrandAgent.CMD_MEM_USAGE:
                    MemUsage.get(agent.agentId).printUsage(0);
                    break;
                case GrandAgent.CMD_SHUTDOWN:
                    agent.shutdown();
                    break;
                case GrandAgent.CMD_ABORT: {
                    ByteBuffer buf = GrandAgentMonitor.intToBuffer(GrandAgent.CMD_OK);
                    GrandAgentMonitor.syncWrite(rudder, buf);
                    agent.abort();
                    return;
                }
                default:
                    BayLog.error("Unknown command: %d", cmd);
            }

            ByteBuffer buf = GrandAgentMonitor.intToBuffer(GrandAgent.CMD_OK);
            agent.netMultiplexer.reqWrite(rudder, buf, null, null, null);
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
            IOUtil.writeInt32(SocketChannelRudder.socketChannel(rudder), GrandAgent.CMD_CLOSE);
        } catch (IOException e) {
            BayLog.error(e);
        }
        close();
    }

    public void close() {
        if(closed)
            return;

        try {
            rudder.close();
        }
        catch (IOException e) {
            BayLog.error(e);
        }
        closed = true;
    }

}
