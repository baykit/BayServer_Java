package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.MemUsage;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.multiplexer.*;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Harbor;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.base.PortBase;

import java.io.IOException;
import java.util.*;

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

    public int timeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    public Multiplexer netMultiplexer;
    public Multiplexer jobMultiplexer;
    public Multiplexer taxiMultiplexer;
    public SpinMultiplexer spinMultiplexer;
    public Multiplexer pegionMultiplexer;

    public final int maxInboundShips;
    public boolean aborted;
    private ArrayList<TimerHandler> timerHandlers = new ArrayList<>();

    public GrandAgent(
            int agentId,
            int maxShips,
            boolean anchorable) {
        this.agentId = agentId;

        this.maxInboundShips = maxShips > 0 ? maxShips : 1;
        this.jobMultiplexer = new JobMultiplexer(this, anchorable);
        this.taxiMultiplexer = new TaxiMultiplexer(this);
        this.spinMultiplexer = new SpinMultiplexer(this);
        this.pegionMultiplexer = new PigeonMultiplexer(this, anchorable);

        switch(BayServer.harbor.netMultiplexer()) {
            case Sensor:
                this.netMultiplexer = new SensingMultiplexer(this, anchorable);
                break;

            case Job:
                this.netMultiplexer = this.jobMultiplexer;
                break;

            case Pigeon:
                this.netMultiplexer = this.pegionMultiplexer;
                break;

            case Spin:
            case Taxi:
            case Train:
                throw new Sink("Multiplexer not supported: %s", Harbor.getMultiplexerTypeName(BayServer.harbor.netMultiplexer()));
        }

        if(!(netMultiplexer instanceof SensingMultiplexer)) {
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        ring();
                    }
                    catch(Throwable e) {
                        BayLog.fatal(e);
                        abort();
                    }
                }
            };

            long period = timeoutSec * 1000; // 10seconds
            timer.scheduleAtFixedRate(task, 0, period);
        }
    }

    public String toString() {
        return "agt#" + agentId;
    }


    ////////////////////////////////////////////
    // Custom methods                         //
    ////////////////////////////////////////////

    public void shutdown() {
        BayLog.debug("%s shutdown aborted=%b", this, aborted);
        if(aborted)
            return;

        aborted = true;
        netMultiplexer.shutdown();

        listeners.forEach(lis -> lis.remove(agentId));

        agents.remove(this);
    }

    public void abort() {
        BayLog.fatal("%s abort", this);
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

    // The timer goes off
    public void ring() {
        for(TimerHandler th: timerHandlers) {
            th.onTimer();
        }
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

    public static GrandAgent add(int agtId, boolean anchorable) {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d", agtId);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorable);
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
