package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.MemUsage;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.monitor.GrandAgentMonitor;
import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CommandReceiver extends Ship {
    public boolean closed = false;

    public void init(int agtId, Rudder rd, Transporter tp) {
        super.init(agtId, rd, tp);
    }

    @Override
    public String toString() {
        return "ComReceiver#" + agentId;
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
        GrandAgent agent = GrandAgent.get(agentId);
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
                    sendCommandToMonitor(agent, GrandAgent.CMD_OK, true);
                    agent.abort();
                    return;
                }
                case GrandAgent.CMD_CATCHUP: {
                    agent.catchUp();
                    return;
                }
                default:
                    BayLog.error("Unknown command: %d", cmd);
            }

            sendCommandToMonitor(agent, GrandAgent.CMD_OK, false);
        } catch (IOException e) {
            BayLog.error(e, "%s Command thread aborted(end)", agent);
            close();
        } finally {
            BayLog.debug("%s Command ended", this);
        }
    }

    public void sendCommandToMonitor(GrandAgent agt, int cmd, boolean sync) throws IOException {

        ByteBuffer buf = GrandAgentMonitor.intToBuffer(cmd);
        if(sync) {
            int n = GrandAgentMonitor.syncWrite(rudder, buf);
            if(n < 4) {
                throw new IOException("Cannot write enough bytes: n=" + n);
            }
        }
        else {
            agt.netMultiplexer.reqWrite(rudder, buf, null, null, null);
        }
    }

    public void end() {
        BayLog.debug("%s end", this);
        try {
            sendCommandToMonitor(null, GrandAgent.CMD_CLOSE, true);
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
            BayLog.debug(e);
        }
        closed = true;
    }

}
