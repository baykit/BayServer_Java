package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.ObjectStore;
import yokohama.baykit.bayserver.util.StringUtil;

import java.util.ArrayList;

public class RudderStateStore extends ObjectStore<RudderState> {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            while(stores.size() < agentId) {
                stores.add(null);
            }
            stores.set(agentId-1, new RudderStateStore());
        }

        @Override
        public void remove(int agentId) {
            stores.set(agentId-1, null);
        }
    }

    /** stores[agent_id - 1] => RudderStateStore */
    static ArrayList<RudderStateStore> stores = new ArrayList<>();

    RudderStateStore() {
        factory = (() -> new RudderState());
    }

    /**
     * print memory usage
     */
    public void printUsage(int indent) {
        BayLog.info("%sRudderStateStore Usage:", StringUtil.indent(indent));
        super.printUsage(indent+1);
    }

    public static void init() {
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static RudderStateStore getStore(int agentId) {
        return stores.get(agentId-1);
    }
}
