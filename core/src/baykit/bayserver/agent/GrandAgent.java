package baykit.bayserver.agent;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.MemUsage;
import baykit.bayserver.Symbol;
import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.docker.Port;
import baykit.bayserver.docker.base.PortBase;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;

public class GrandAgent extends Thread {

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
    public static Map<Integer, GrandAgent> agents = new HashMap<>();
    public static List<LifecycleListener> listeners = new ArrayList<>();

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
            boolean anchorable) throws IOException {
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
            commandReceiver.comRecvChannel.configureBlocking(false);
            commandReceiver.comRecvChannel.register(selector, SelectionKey.OP_READ);

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
                    if(key.channel() == commandReceiver.comRecvChannel)
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
            }

        }
        catch (Throwable e) {
            // If error occurs, grand agent ends
            BayLog.error(e);
        }
        finally {
            BayLog.info("%s end", this);
            abort(null);
        }
    }


    public void shutdown() {
        BayLog.debug("%s shutdown", this);
        if(acceptHandler != null)
            acceptHandler.shutdown();
        abort(null);
    }

    public void abort(Throwable err) {
        if(err != null) {
            BayLog.fatal("%s abort", this);
            BayLog.fatal(err);
        }

        commandReceiver.end();
        listeners.forEach(lis -> lis.remove(agentId));

        agents.remove(this);

        clean();
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

    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(this, readCh, writeCh);
    }

    private void clean() {
        nonBlockingHandler.closeAll();
    }

    /////////////////////////////////////////////////////////////////////////////
    // static methods                                                          //
    /////////////////////////////////////////////////////////////////////////////
    public static void init(
            int agentIds[],
            Map<ServerSocketChannel, Port> anchorablePortMap,
            Map<DatagramChannel, Port> unanchorablePortMap,
            int maxShips) throws IOException {
        GrandAgent.agentCount = agentIds.length;
        GrandAgent.anchorablePortMap = anchorablePortMap;
        GrandAgent.unanchorablePortMap = unanchorablePortMap != null ? unanchorablePortMap : new HashMap<>();
        GrandAgent.maxShips = maxShips;
    }

    public static GrandAgent get(int id) {
        return agents.get(id);
    }

    public static void add(int agtId, boolean anchorable) throws IOException {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d", agtId);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorable);
        agents.put(agtId, agt);

        listeners.forEach(lis -> lis.add(agt.agentId));
    }

    public static void addLifecycleListener(LifecycleListener lis) {
        listeners.add(lis);
    }



    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////


}
