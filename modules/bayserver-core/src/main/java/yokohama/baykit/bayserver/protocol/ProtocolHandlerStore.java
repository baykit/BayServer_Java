package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.ObjectStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocol handler pool
 */
public class ProtocolHandlerStore extends ObjectStore<ProtocolHandler> {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            protoMap.values().forEach(ifo -> ifo.addAgent(agentId));
        }

        @Override
        public void remove(int agentId) {
            protoMap.values().forEach(ifo -> ifo.removeAgent(agentId));
        }
    }


    static class ProtocolInfo {
        final String protocol;
        final boolean serverMode;
        final ProtocolHandlerFactory protocolHandlerFactory;

        /** Agent ID => ProtocolHandlerStore */
        final Map<Integer, ProtocolHandlerStore> stores = new HashMap<>();

        public ProtocolInfo(String proto, boolean svrMode, ProtocolHandlerFactory protocolHandlerFactory) {
            this.protocol = proto;
            this.serverMode = svrMode;
            this.protocolHandlerFactory = protocolHandlerFactory;
        }


        public void addAgent(int agtId) {
            PacketStore store = PacketStore.getStore(protocol, agtId);
            stores.put(agtId, new ProtocolHandlerStore(protocol, serverMode, protocolHandlerFactory, store));
        }

        public void removeAgent(int agtId) {
            stores.remove(agtId);
        }
    }

    static Map<String, ProtocolInfo> protoMap = new HashMap<>();

    String protocol;
    boolean serverMode;

    ProtocolHandlerStore(
            String protocol,
            boolean svrMode,
            ProtocolHandlerFactory<?, ?, ?> phFactory,
            PacketStore pktStore) {
        this.protocol = protocol;
        this.serverMode = svrMode;
        factory = (() -> {
            return phFactory.createProtocolHandler(pktStore);
        });
    }

    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sProtocolHandlerStore(%s%s) Usage:", StringUtil.indent(indent), protocol, serverMode ? "s" : "c");
        super.printUsage(indent+1);
    }

    public static void init() {
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static ProtocolHandlerStore getStore(String protocol, boolean svrMode, int agentId) {
        return protoMap.get(constructProtocol(protocol, svrMode)).stores.get(agentId);
    }

    public static List<ProtocolHandlerStore> getStores(int agentId) {
        List<ProtocolHandlerStore> storeList = new ArrayList<>();
        protoMap.values().forEach(ifo -> {
            storeList.add(ifo.stores.get(agentId));
        });
        return storeList;
    }

    public static void registerProtocol(
            String protocol,
            boolean svrMode,
            ProtocolHandlerFactory pHndFactory) {
        String key = constructProtocol(protocol, svrMode);
        if(!protoMap.containsKey(key)) {
            protoMap.put(key, new ProtocolInfo(protocol, svrMode, pHndFactory));
        }
    }

    static String constructProtocol(String protocol, boolean svrMode) {
        if(svrMode)
            return protocol + "-s";
        else
            return protocol + "-c";
    }
}
