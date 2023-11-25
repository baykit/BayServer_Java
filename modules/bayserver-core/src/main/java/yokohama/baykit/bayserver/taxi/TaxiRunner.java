package yokohama.baykit.bayserver.taxi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.TimerHandler;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class TaxiRunner implements TimerHandler {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            while (runners.size() < agentId) {
                runners.add(null);
            }
            TaxiRunner.runners.set(agentId - 1, new TaxiRunner(GrandAgent.get(agentId)));
        }

        @Override
        public void remove(int agentId) {
            runners.get(agentId - 1).terminate();
            runners.set(agentId - 1, null);
        }
    }

    static int maxTaxis;
    static ArrayList<TaxiRunner> runners = new ArrayList<>();

    final GrandAgent agent;
    final ExecutorService exe;
    final ArrayList<Taxi> runningTaxies = new ArrayList<>();

    public TaxiRunner(GrandAgent agt) {
        this.agent = agt;
        this.exe = Executors.newFixedThreadPool(maxTaxis);
        this.agent.addTimerHandler(this);
    }

    //////////////////////////////////////////////
    // Implements TimerHandler
    //////////////////////////////////////////////
    @Override
    public void onTimer() {
        synchronized (runningTaxies) {
            for(Taxi txi: runningTaxies)
                txi.onTimer();
        }
    }

    //////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////
    void terminate() {
        BayLog.debug("%s terminate TaxiRunner", agent);
        agent.removeTimerHandler(this);
        exe.shutdown();
    }

    boolean submit(Taxi txi) {
        try {
            exe.submit(() -> {
                if(agent.aborted) {
                    BayLog.error("Agent is aborted");
                    return;
                }
                synchronized (runningTaxies) {
                    runningTaxies.add(txi);
                }
                try {
                    txi.run();
                }
                catch(Throwable e) {
                    BayLog.fatal(e);
                    agent.reqShutdown();
                }
                finally {
                    synchronized (runningTaxies) {
                        runningTaxies.remove(txi);
                    }
                }
            });
            return true;
        } catch(RejectedExecutionException e) {
            BayLog.error(e);
            return false;
        }
    }

    //////////////////////////////////////////////
    // Static methods
    //////////////////////////////////////////////
    public static void init(int maxTaxis) {
        if(maxTaxis <= 0)
            throw new IllegalArgumentException();
        TaxiRunner.maxTaxis = maxTaxis;
        GrandAgent.addLifecycleListener(new TaxiRunner.AgentListener());
    }

    public static boolean post(int agtId, Taxi txi) {
        BayLog.debug("Agt#%d post taxi: thread=%s taxi=%s", agtId, Thread.currentThread().getName(), txi);
        return TaxiRunner.runners.get(agtId - 1).submit(txi);
    }
}
