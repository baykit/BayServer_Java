package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.MemUsage;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.base.PortBase;

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
    static int maxShips;
    static int maxAgentId;
    public static Map<Integer, GrandAgent> agents = new HashMap<>();
    public static List<LifecycleListener> listeners = new ArrayList<>();

    int selectTimeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    Map<ServerSocketChannel, Port> anchorablePortMap;
    Map<DatagramChannel, Port> unanchorablePortMap;
    public boolean anchorable;
    final Selector selector;
    public SpinHandler spinHandler;
    public NonBlockingHandler nonBlockingHandler;
    public AcceptHandler acceptHandler;
    public final int maxInboundShips;
    boolean aborted;
    public Map<DatagramChannel, Transporter> unanchorableTransporters = new HashMap<>();
    public CommandReceiver commandReceiver;
    ArrayList<TimerHandler> timerHandlers = new ArrayList<>();

    public GrandAgent(
            int agentId,
            int maxShips,
            Map<ServerSocketChannel, Port> anchorablePortMap,
            Map<DatagramChannel, Port> unanchorablePortMap,
            boolean anchorable) throws IOException {
        super("GrandAgent#" + agentId);
        this.agentId = agentId;
        this.anchorable = anchorable;

        if(anchorable) {
            this.anchorablePortMap = anchorablePortMap;
            this.acceptHandler = new AcceptHandler(this, anchorablePortMap);
        }
        else {
            this.unanchorablePortMap = unanchorablePortMap != null ? unanchorablePortMap : new HashMap<>();
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
            if(!anchorable) {
                for (DatagramChannel ch : unanchorablePortMap.keySet()) {
                    Port p = unanchorablePortMap.get(ch);
                    Transporter tp = p.newTransporter(this, ch);
                    unanchorableTransporters.put(ch, tp);
                    nonBlockingHandler.addChannelListener(ch, tp);
                    nonBlockingHandler.askToStart(ch);
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
                    //BayLog.debug(this + " selected key=" + key);
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
                    for(TimerHandler th: timerHandlers) {
                        th.onTimer();
                    }
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

    public void addTimerHandler(TimerHandler th) {
        timerHandlers.add(th);
    }

    public void removeTimerHandler(TimerHandler th) {
        timerHandlers.remove(th);
    }

    private void clean() {
        nonBlockingHandler.closeAll();
    }

    /////////////////////////////////////////////////////////////////////////////
    // static methods                                                          //
    /////////////////////////////////////////////////////////////////////////////
    public static void init(
            int agentIds[],
            int maxShips) throws IOException {
        GrandAgent.agentCount = agentIds.length;
        GrandAgent.maxShips = maxShips;
    }

    public static GrandAgent get(int id) {
        return agents.get(id);
    }

    public static void add(
            int agtId,
            Map<ServerSocketChannel, Port> anchorablePortMap,
            Map<DatagramChannel, Port> unanchorablePortMap,
            boolean anchorable) throws IOException {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d", agtId);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorablePortMap, unanchorablePortMap, anchorable);
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
