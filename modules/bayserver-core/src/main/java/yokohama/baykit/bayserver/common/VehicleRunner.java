package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.TimerHandler;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class VehicleRunner {

    class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            while (services.size() < agentId) {
                services.add(null);
            }
            services.set(agentId - 1, new VehicleService(GrandAgent.get(agentId)));
        }

        @Override
        public synchronized void remove(int agentId) {
            BayLog.debug("agt#%d remove VehicleRunner", agentId);
            services.get(agentId - 1).terminate();
            services.set(agentId - 1, null);
        }
    }

    int maxVehicles;
    ArrayList<VehicleService> services = new ArrayList<>();

    class VehicleService implements TimerHandler {
        final GrandAgent agent;
        final ExecutorService exe;
        final ArrayList<Vehicle> runnings = new ArrayList<>();

        public VehicleService(GrandAgent agt) {
            this.agent = agt;
            this.exe = Executors.newFixedThreadPool(maxVehicles);
            this.agent.addTimerHandler(this);
        }

        //////////////////////////////////////////////
        // Implements TimerHandler
        //////////////////////////////////////////////
        @Override
        public void onTimer() {
            synchronized (runnings) {
                for(Vehicle vcl: runnings)
                    vcl.onTimer();
            }
        }

        //////////////////////////////////////////////
        // Private methods
        //////////////////////////////////////////////
        private void terminate() {
            BayLog.debug("%s terminate VehicleRunner: %s", agent, this);
            agent.removeTimerHandler(this);
            exe.shutdown();
        }

        private boolean submit(Vehicle vcl) {
            try {
                exe.submit(() -> {
                    if(agent.aborted) {
                        BayLog.fatal("%s Agent is aborted", agent);
                        return;
                    }
                    synchronized (runnings) {
                        runnings.add(vcl);
                    }
                    try {
                        vcl.run();
                    }
                    catch(Throwable e) {
                        BayLog.fatal(e);
                        agent.shutdown();
                    }
                    finally {
                        synchronized (runnings) {
                            runnings.remove(vcl);
                        }
                    }
                });
                return true;
            } catch(RejectedExecutionException e) {
                BayLog.error(e);
                return false;
            }
        }
    }

    //////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////
    public void init(int max) {
        if(max <= 0)
            throw new IllegalArgumentException();
        this.maxVehicles = max;
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public boolean post(int agtId, Vehicle vcl) {
        return services.get(agtId - 1).submit(vcl);
    }
}
