package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.MemUsage;
import yokohama.baykit.bayserver.agent.multiplexer.SpinMultiplexer;
import yokohama.baykit.bayserver.agent.multiplexer.TaxiMultiplexer;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.base.PortBase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrandAgent {

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

    public int selectTimeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    public Multiplexer multiplexer;
    public Multiplexer taxiMultiplexer;
    public SpinMultiplexer spinMultiplexer;

    public final int maxInboundShips;
    public boolean aborted;
    public ArrayList<TimerHandler> timerHandlers = new ArrayList<>();

    public GrandAgent(
            int agentId,
            int maxShips) {
        this.agentId = agentId;

        this.maxInboundShips = maxShips > 0 ? maxShips : 1;
        this.taxiMultiplexer = new TaxiMultiplexer(this);
        this.spinMultiplexer = new SpinMultiplexer(this);
    }

    public String toString() {
        return "agt#" + agentId;
    }


    ////////////////////////////////////////////
    // Custom methods                         //
    ////////////////////////////////////////////
    public void setMultiplexer(Multiplexer multiplexer) {
        this.multiplexer = multiplexer;
    }

    public void shutdown() {
        BayLog.debug("%s shutdown", this);
        aborted = true;
        multiplexer.shutdown();

        listeners.forEach(lis -> lis.remove(agentId));

        agents.remove(this);
    }

    public void abort() {
        BayLog.fatal("%s abort", this);
    }

    /**
     * Another thread requests to shut down this GrandAgent
     */
    public void reqShutdown() {
        aborted = true;
    }


    public void reloadCert() {
        for(Port port : BayServer.anchorablePortMap.values()) {
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

    public void addTimerHandler(TimerHandler th) {
        timerHandlers.add(th);
    }

    public void removeTimerHandler(TimerHandler th) {
        timerHandlers.remove(th);
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

    public static GrandAgent add(
            int agtId) {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d", agtId);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips);
        agents.put(agtId, agt);

        listeners.forEach(lis -> lis.add(agt.agentId));
        return agt;
    }

    public static void addLifecycleListener(LifecycleListener lis) {
        listeners.add(lis);
    }



    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////
}
