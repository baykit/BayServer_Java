package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.util.BlockingIOException;
import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrandAgentMonitor {

    static int numAgents;
    static int curId;
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
        try {
            Thread.sleep(1 * 1000); // lazy implementation
        }
        catch(InterruptedException e) {
            BayLog.error(e);
        }
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
            int numAgents) throws IOException
    {
        GrandAgentMonitor.numAgents = numAgents;

        if(!BayServer.unanchorablePortMap.isEmpty()) {
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

        GrandAgent agt = GrandAgent.add(agtId, anchorable);
        agt.netMultiplexer.runCommandReceiver(sendPipe.source(), recvPipe.sink());

        monitors.put(
                agtId,
                new GrandAgentMonitor(
                        agtId,
                        anchorable,
                        sendPipe.sink(),
                        recvPipe.source()));

        agt.netMultiplexer.start();
    }

    static synchronized void agentAborted(int agtId, boolean anchorable) {

        BayLog.error(BayMessage.get(Symbol.MSG_GRAND_AGENT_SHUTDOWN, agtId));

        monitors.remove(agtId);

        if(!finale) {
            if (monitors.size() < numAgents) {
                try {
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
        // print memory usage
        BayLog.info("BayServer MemUsage");

        String version = System.getProperty("java.version");
        String runtimeVersion = System.getProperty("java.runtime.version");
        String vmVersion = System.getProperty("java.vm.version");
        String specVersion = System.getProperty("java.specification.version");

        BayLog.info(" Java Version: %s", version);
        BayLog.info(" Java Runtime Version: %s", runtimeVersion);
        BayLog.info(" Java VM Version: %s", vmVersion);
        BayLog.info(" Java Specification Version: %s", specVersion);

        Runtime runtime = Runtime.getRuntime();
        int numberOfProcessors = runtime.availableProcessors();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;

        BayLog.info(" Number of processors: %d", numberOfProcessors);
        BayLog.info(" Max memory: %d MBytes", maxMemory / (1024 * 1024));
        BayLog.info(" Allocated memory: %d MBytes", allocatedMemory / (1024 * 1024));
        BayLog.info(" Free memory: %d MBytes", freeMemory / (1024 * 1024));
        BayLog.info(" Used memory: %d MBytes", usedMemory / (1024 * 1024));

        for (GrandAgentMonitor mon : monitors.values()) {
            try {
                mon.printUsage();
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
    }
}
