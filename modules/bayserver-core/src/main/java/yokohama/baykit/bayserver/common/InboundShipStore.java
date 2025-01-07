package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.ObjectStore;
import yokohama.baykit.bayserver.util.StringUtil;

import java.util.ArrayList;

public class InboundShipStore extends ObjectStore<InboundShip> {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            while(stores.size() < agentId) {
                stores.add(null);
            }
            stores.set(agentId-1, new InboundShipStore());
        }

        @Override
        public void remove(int agentId) {
            stores.set(agentId-1, null);
        }
    }

    /** stores[agent_id - 1] => InboundShipStore */
    static ArrayList<InboundShipStore> stores = new ArrayList<>();

    InboundShipStore() {
        factory = (() -> new InboundShip());
    }

    /**
     * print memory usage
     */
    public void printUsage(int indent) {
        BayLog.info("%sInboundShipStore Usage:", StringUtil.indent(indent));
        super.printUsage(indent+1);
    }

    public static void init() {
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static InboundShipStore getStore(int agentId) {
        return stores.get(agentId-1);
    }
}
