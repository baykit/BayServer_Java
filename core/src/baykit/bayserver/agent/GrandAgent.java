package baykit.bayserver.agent;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.MemUsage;
import baykit.bayserver.Symbol;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.docker.Port;
import baykit.bayserver.docker.base.PortBase;
import baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;

public class GrandAgent extends Thread {

    public interface GrandAgentLifecycleListener {
        void add(int agentId);
        void remove(int agentId);
    }

    class CommandReceiver {
        GrandAgent agent;
        Pipe.SourceChannel readCh;
        Pipe.SinkChannel writeCh;
        boolean aborted = false;

        public CommandReceiver(GrandAgent agent, Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
            this.agent = agent;
            this.readCh = readCh;
            this.writeCh = writeCh;
        }

        @Override
        public String toString() {
            return "ComReceiver#" + agent.agentId;
        }

        public void onPipeReadable()
        {
            try {
                int cmd = IOUtil.readInt32(readCh);

                BayLog.debug("%s receive command %d pipe=%s", agent, cmd, readCh);
                switch (cmd) {
                    case GrandAgent.CMD_RELOAD_CERT:
                        agent.reloadCert();
                        break;
                    case GrandAgent.CMD_MEM_USAGE:
                        agent.printUsage();
                        break;
                    case GrandAgent.CMD_SHUTDOWN:
                        agent.shutdown();
                        aborted = true;
                        break;
                    case GrandAgent.CMD_ABORT:
                        IOUtil.writeInt32(writeCh, GrandAgent.CMD_OK);
                        agent.abort();
                        return;
                    default:
                        BayLog.error("Unknown command: %d", cmd);
                }

                IOUtil.writeInt32(writeCh, GrandAgent.CMD_OK);
            }
            catch(IOException e) {
                BayLog.error(e, "%s Command thread aborted(end)", agent);
            }
            finally {
                BayLog.debug("%s Command ended", this);
            }
        }

        public void abort()
        {
            BayLog.debug("%s end", this);
            try {
                IOUtil.writeInt32(writeCh, GrandAgent.CMD_CLOSE);
            }
            catch(IOException e) {
                BayLog.error(e);
            }
        }
    }

    public static final int CMD_OK = 0;
    public static final int CMD_CLOSE = 1;
    public static final int CMD_RELOAD_CERT = 2;
    public static final int CMD_MEM_USAGE = 3;
    public static final int CMD_SHUTDOWN = 4;
    public static final int CMD_ABORT = 5;

    public static final int SELECT_TIMEOUT_SEC = 10;

    static int agentCount;
    static Map<ServerSocketChannel, Port> anchorablePortMap;
    static Map<DatagramChannel, Port> unanchorablePortMap;
    static int maxShips;
    static int maxAgentId;
    static boolean finale;
    public static List<GrandAgent> agents = new ArrayList<>();
    public static List<GrandAgentLifecycleListener> listeners = new ArrayList<>();
    public static List<GrandAgentMonitor> monitors = new ArrayList<>();

    int selectTimeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    final Selector selector;
    public SpinHandler spinHandler;
    public NonBlockingHandler nonBlockingHandler;
    public AcceptHandler acceptHandler;
    public final int maxInboundShips;
    boolean aborted;
    public boolean anchorable;
    public Map<DatagramChannel, Transporter> unanchorableTransporters = new HashMap<>();
    public CommandReceiver commandReceiver;

    public GrandAgent(
            int agentId,
            int maxShips,
            boolean anchorable,
            Pipe recvPipe,
            Pipe sendPipe) throws IOException {
        super("GrandAgent#" + agentId);
        this.agentId = agentId;
        this.anchorable = anchorable;

        if(anchorable) {
            this.acceptHandler = new AcceptHandler(this, anchorablePortMap);
        }
        this.spinHandler = new SpinHandler(this);
        this.nonBlockingHandler = new NonBlockingHandler(this);
        this.selector = Selector.open();
        this.maxInboundShips = maxShips > 0 ? maxShips : 1;
        this.commandReceiver = new CommandReceiver(this, recvPipe.source(), sendPipe.sink());
    }

    public String toString() {
        return "agt#" + agentId;
    }


    /////////////////////////////////////////////////////////////////////////////
    // override methods                                                        //
    /////////////////////////////////////////////////////////////////////////////

    @Override
    public void run() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        try {
            commandReceiver.readCh.configureBlocking(false);
            commandReceiver.readCh.register(selector, SelectionKey.OP_READ);

            // Set up unanchorable channel
            for(DatagramChannel ch : unanchorablePortMap.keySet()) {
                Port p = unanchorablePortMap.get(ch);
                Transporter tp = p.newTransporter(this, ch);
                unanchorableTransporters.put(ch, tp);
                nonBlockingHandler.addChannelListener(ch, tp);
                nonBlockingHandler.askToStart(ch);
                if(!anchorable) {
                    nonBlockingHandler.askToRead(ch);
                }
            }

            boolean busy = true;
            while (true) {
                try {
                    if(acceptHandler != null) {
                        boolean testBusy = acceptHandler.chCount >= maxInboundShips;
                        if (testBusy != busy) {
                            busy = testBusy;
                            if(busy) {
                                acceptHandler.onBusy();
                            }
                            else {
                                acceptHandler.onFree();
                            }
                        }
                    }

                    /*
                    System.err.println("selecting...");
                    selector.keys().forEach((key) -> {
                        System.err.println(this + " readable=" + (key.isValid() ? "" + key.interestOps() : "invalid "));
                    });
                    */

                    int count;
                    if (aborted) {
                        // agent finished
                        BayLog.debug("%s End loop", this);
                        break;
                    }
                    else if (!spinHandler.isEmpty()) {
                        count = selector.selectNow();
                    }
                    else {
                        count = selector.select(selectTimeoutSec * 1000L);
                    }

                    //BayLog.debug(this + " select count=" + count);
                    boolean processed = nonBlockingHandler.registerChannelOps() > 0;

                    Set<SelectionKey> selKeys = selector.selectedKeys();
                    if(selKeys.isEmpty()) {
                        processed |= spinHandler.processData();
                    }

                    for(Iterator<SelectionKey> it = selKeys.iterator(); it.hasNext(); ) {
                        SelectionKey key = it.next();
                        it.remove();
                        if(key.channel() == commandReceiver.readCh)
                            commandReceiver.onPipeReadable();
                        else if(key.isAcceptable())
                            acceptHandler.onAcceptable(key);
                        else
                            nonBlockingHandler.handleChannel(key);
                        processed = true;
                    }

                    if(!processed) {
                        // timeout check if there is nothing to do
                        nonBlockingHandler.closeTimeoutSockets();
                        spinHandler.stopTimeoutSpins();
                    }

                } catch (Throwable e) {
                    throw e;    // If error occur, grand agent ends
                }
            }

        }
        catch (Throwable e) {
            BayLog.error(e);
        }
        finally {
            BayLog.info("%s end", this);
            commandReceiver.abort();
            for( GrandAgentLifecycleListener lis: GrandAgent.listeners) {
                lis.remove(agentId);
            }
        }
    }


    public void shutdown() {
        BayLog.debug("%s shutdown", this);
        if(acceptHandler != null)
            acceptHandler.shutdown();
        aborted = true;
        selector.wakeup();
    }

    public void abort() {
        aborted = true;
    }


    public void reloadCert() {
        for(Port port : anchorablePortMap.values()) {
            if(port.secure()) {
                PortBase pbase = (PortBase)port;
                try {
                    pbase.secureDocker.reloadCert();
                } catch (Exception e) {
                    BayLog.error(e);
                }
            }
        }
    }

    public void printUsage() {
        // print memory usage
        BayLog.info("Agent#%d MemUsage", agentId);
        MemUsage.get(agentId).printUsage(1);
    }


    /////////////////////////////////////////////////////////////////////////////
    // static methods                                                          //
    /////////////////////////////////////////////////////////////////////////////
    public static void init(int count, Map<ServerSocketChannel, Port> anchorablePortMap, Map<DatagramChannel, Port> unanchorablePortMap, int maxShips) throws IOException {
        GrandAgent.agentCount = count;
        GrandAgent.anchorablePortMap = anchorablePortMap;
        GrandAgent.unanchorablePortMap = unanchorablePortMap;
        GrandAgent.maxShips = maxShips;
        if(!unanchorablePortMap.isEmpty()) {
            add(false);
            agentCount++;
        }
        for(int i = 0; i < count; i++) {
            add(true);
        }
    }

    public static GrandAgent get(int id) {
        return agents.stream().filter(agt -> agt.agentId == id).findFirst().get();
    }

    public static synchronized void add(boolean anchorable) throws IOException {
        Pipe sendPipe = Pipe.open();
        Pipe recvPipe = Pipe.open();
        int agtId = ++maxAgentId;
        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorable, sendPipe, recvPipe);
        agents.add(agt);

        listeners.forEach(lis -> lis.add(agt.agentId));

        agt.start();

        GrandAgentMonitor mon = new GrandAgentMonitor(agtId, anchorable, sendPipe, recvPipe);
        monitors.add(mon);
    }

    /**
     * Reload certificate for all agents
     */
    public static void reloadCertAll() throws IOException {
        BayLog.debug("Reload all");
        for(GrandAgentMonitor mon: monitors) {
            mon.reloadCert();
        }
    }


    /**
     * Restart all agents
     */
    public static void restartAll() throws IOException {
        BayLog.debug("Restart all");
        List<GrandAgentMonitor> oldMonitors = new ArrayList<>();
        oldMonitors.addAll(monitors);
        for(GrandAgentMonitor mon: oldMonitors) {
            mon.shutdown();
        }
    }


    public static void shutdownAll() throws IOException {
        BayLog.debug("Shutdown all");
        finale = true;
        List<GrandAgentMonitor> oldMonitors = new ArrayList<>();
        oldMonitors.addAll(monitors);
        for(GrandAgentMonitor mon: oldMonitors) {
            mon.shutdown();
        }
    }

    public static void abortAll() {
        BayLog.debug("Shutdown all");
        finale = true;
        List<GrandAgentMonitor> oldMonitors = new ArrayList<>();
        oldMonitors.addAll(monitors);
        for(GrandAgentMonitor mon: oldMonitors) {
            mon.abort();
        }
        System.exit(1);
    }


    public static void printUsageAll() {
        for (GrandAgentMonitor mon : monitors) {
            try {
                mon.printUsage();
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
    }

    public static void addLifecycleListener(GrandAgentLifecycleListener lis) {
        listeners.add(lis);
    }

    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////

    static synchronized void agentAborted(int agtId, boolean anchorable) {

        BayLog.info(BayMessage.get(Symbol.MSG_GRAND_AGENT_SHUTDOWN, agtId));

        agents.removeIf(agt -> agt.agentId == agtId);
        //listeners.forEach(lis -> lis.remove(agtId));

        monitors.removeIf(mon -> mon.agentId == agtId);

       if(!finale) {
            if (agents.size() < agentCount) {
                try {
                    add(anchorable);
                } catch (IOException e) {
                    BayLog.error(e);
                }
            }
        }
    }
}
