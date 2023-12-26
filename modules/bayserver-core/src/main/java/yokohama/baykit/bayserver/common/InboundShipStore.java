package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.ObjectStore;

import java.util.HashMap;
import java.util.Map;

public class InboundShipStore extends ObjectStore<InboundShip> {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            stores.put(agentId, new InboundShipStore());
        }

        @Override
        public void remove(int agentId) {
            stores.remove(agentId);
        }
    }

    /** Agent id => InboundShipStore */
    static Map<Integer, InboundShipStore> stores = new HashMap<>();

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
        return stores.get(agentId);
    }
}
