package baykit.bayserver.agent;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.Symbol;
import baykit.bayserver.docker.Port;
import baykit.bayserver.util.BlockingIOException;
import baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrandAgentMonitor {

    static int numAgents;
    static int curId;
    static Map<ServerSocketChannel, Port> anchorablePortMap;
    static Map<DatagramChannel, Port> unanchorablePortMap;
    public static Map<Integer, GrandAgentMonitor> monitors = new HashMap<>();
    static boolean finale;


    int agentId;
    boolean anchorable;
    Pipe.SinkChannel comSendChannel;
    public Pipe.SourceChannel comRecvChannel;

    GrandAgentMonitor(int agentId, boolean anchorable, Pipe.SinkChannel comSendChannel, Pipe.SourceChannel comRecvChannel) {
        this.agentId = agentId;
        this.anchorable = anchorable;
        this.comSendChannel = comSendChannel;
        this.comRecvChannel= comRecvChannel;
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
                int res = IOUtil.readInt32(comRecvChannel);
                if (res == GrandAgent.CMD_CLOSE) {
                    BayLog.debug("%s read Close", this);
                    GrandAgentMonitor.agentAborted(agentId, anchorable);
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
        BayLog.debug("%s send command %s pipe=%s", this, cmd, comSendChannel);
        IOUtil.writeInt32(comSendChannel, cmd);
    }

    public void close()
    {
        try {
            comSendChannel.close();
            comRecvChannel.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }


    /////////////////////////////////////////////////
    // static methods                              //
    /////////////////////////////////////////////////
    public static void init(
            int numAgents,
            Map<ServerSocketChannel, Port> anchorablePortMap,
            Map<DatagramChannel, Port> unanchorablePortMap) throws IOException
    {
        GrandAgentMonitor.numAgents = numAgents;
        GrandAgentMonitor.anchorablePortMap = anchorablePortMap;
        GrandAgentMonitor.unanchorablePortMap = unanchorablePortMap;

        if(!unanchorablePortMap.isEmpty()) {
            add(false);
            GrandAgentMonitor.numAgents++;
        }

        for(int i = 0; i < numAgents; i++) {
            add(true);
        }
    }

    static void add(boolean anchorable) throws IOException
    {
        int agtId = ++curId;
        if (agtId > 100) {
            BayLog.error("Too many agents started");
            System.exit(1);
        }

        Pipe sendPipe = Pipe.open();
        Pipe recvPipe = Pipe.open();

        GrandAgent.add(agtId, anchorable);

        GrandAgent agt = GrandAgent.get(agtId);
        Thread t = new Thread(agt);
        agt.runCommandReceiver(sendPipe.source(), recvPipe.sink());
        t.start();

        monitors.put(
                agtId,
                new GrandAgentMonitor(
                        agtId,
                        anchorable,
                        sendPipe.sink(),
                        recvPipe.source()));
    }

    static synchronized void agentAborted(int agtId, boolean anchorable) {

        BayLog.info(BayMessage.get(Symbol.MSG_GRAND_AGENT_SHUTDOWN, agtId));

        monitors.remove(agtId);

        if(!finale) {
            if (monitors.size() < numAgents) {
                try {
                    GrandAgent.add(-1, anchorable);
                    add(anchorable);
                }
                catch (IOException e) {
                    BayLog.error(e);
                }
            }
        }
    }

    /**
     * Reload certificate for all agents
     */
    public static void reloadCertAll() throws IOException {
        BayLog.debug("Reload all");
        for(GrandAgentMonitor mon: monitors.values()) {
            mon.reloadCert();
        }
    }


    /**
     * Restart all agents
     */
    public static void restartAll() throws IOException {
        BayLog.debug("Restart all");
        List<GrandAgentMonitor> oldMonitors = new ArrayList<>(monitors.values());
        for(GrandAgentMonitor mon: oldMonitors) {
            mon.shutdown();
        }
    }


    public static void shutdownAll() throws IOException {
        BayLog.debug("Shutdown all");
        finale = true;
        List<GrandAgentMonitor> oldMonitors = new ArrayList<>(monitors.values());
        for(GrandAgentMonitor mon: oldMonitors) {
            mon.shutdown();
        }
    }


    public static void printUsageAll() {
        for (GrandAgentMonitor mon : monitors.values()) {
            try {
                mon.printUsage();
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
    }
}
