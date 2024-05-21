package yokohama.baykit.bayserver.agent.monitor;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.rudder.AsynchronousSocketChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GrandAgentMonitor extends Thread {

    static int numAgents;
    static int curId;
    public static Map<Integer, GrandAgentMonitor> monitors = new HashMap<>();
    static boolean finale;


    int agentId;
    boolean anchorable;
    Rudder rudder;

    GrandAgentMonitor(int agentId, boolean anchorable, Rudder rd) {
        this.agentId = agentId;
        this.anchorable = anchorable;
        this.rudder = rd;
    }

    @Override
    public String toString()
    {
        return "Monitor#" + agentId;
    }

    /////////////////////////////////////////////////
    // Implements Runnable                         //
    /////////////////////////////////////////////////

    @Override
    public void run() {
        try {
            while(true) {
                ByteBuffer buf = ByteBuffer.allocate(4);
                syncRead(rudder, buf);
                buf.flip();
                int res = bufferToInt(buf);
                if (res == GrandAgent.CMD_CLOSE) {
                    BayLog.debug("%s read Close", this);
                    break;
                }
                else {
                    BayLog.debug("%s read OK: %d", this, res);
                }
            }
        }
        catch (IOException e) {
            BayLog.fatal(e);
        }
        GrandAgentMonitor.agentAborted(agentId, anchorable);
    }


    /////////////////////////////////////////////////
    // Custom methods                              //
    /////////////////////////////////////////////////

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

    /////////////////////////////////////////////////
    // Private methods                             //
    /////////////////////////////////////////////////

    private void send(int cmd) throws IOException {
        BayLog.debug("%s send command %s rd=%s", this, cmd, rudder);
        ByteBuffer buf = intToBuffer(cmd);
        syncWrite(rudder, buf);
    }

    private void close()
    {
        try {
            rudder.close();
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

        GrandAgent agt = GrandAgent.add(agtId, anchorable);

        Rudder rd1, rd2;

        if(agt.netMultiplexer.useAsyncAPI()) {
            AsynchronousSocketChannel[] pair = IOUtil.asynchronousSocketChannelPair();
            rd1 = new AsynchronousSocketChannelRudder(pair[0]);
            rd2 = new AsynchronousSocketChannelRudder(pair[1]);
        }
        else {
            SocketChannel[] pair = IOUtil.socketChannelPair();
            rd1 = new SocketChannelRudder(pair[0]);
            rd2 = new SocketChannelRudder(pair[1]);
        }
        agt.addCommandReceiver(rd1);

        GrandAgentMonitor mon = new GrandAgentMonitor(
                agtId,
                anchorable,
                rd2);

        monitors.put(agtId, mon);
        mon.start();

        agt.start();
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

    public static int bufferToInt(ByteBuffer buf) {
        buf.order(ByteOrder.BIG_ENDIAN);
        return buf.getInt();
    }

    public static ByteBuffer intToBuffer(int val) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(val);
        buf.flip();
        return buf;
    }

    public static int syncRead(Rudder rd, ByteBuffer buf) throws IOException {
        if(rd instanceof AsynchronousSocketChannelRudder) {

            Future<Integer> ft = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(rd).read(buf);
            try {
                return ft.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }
        else {
            return SocketChannelRudder.socketChannel(rd).read(buf);
        }

    }

    public static void syncWrite(Rudder rd, ByteBuffer buf) throws IOException {
        if(rd instanceof AsynchronousSocketChannelRudder) {

            Future<Integer> ft = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(rd).write(buf);
            try {
                ft.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }
        else {
            SocketChannelRudder.socketChannel(rd).write(buf);
        }

    }
}