package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.ObjectStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command pool
 */
public class CommandStore<C extends Command> implements Reusable {

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
        final CommandFactory commandFactory;

        /** Agent ID => PacketStore */
        final Map<Integer, CommandStore> stores = new HashMap<>(32);

        public ProtocolInfo(String proto, CommandFactory commandFactory) {
            this.protocol = proto;
            this.commandFactory = commandFactory;
        }

        public void addAgent(int agtId) {
            CommandStore store = new CommandStore(protocol, commandFactory);
            stores.put(agtId, store);
        }

        public void removeAgent(int agtId) {
            stores.remove(agtId);
        }
    }

    static Map<String, ProtocolInfo> protoMap = new HashMap<>();

    final String protocol;
    final ArrayList<ObjectStore<C>> storeMap = new ArrayList<>();
    final CommandFactory<C> factory;

    private CommandStore(String protocol, CommandFactory<C> factory) {
        this.protocol = protocol;
        this.factory = factory;
        for(int i = 0; i < 128; i++) {
            storeMap.add(null);
        }
    }


    public void reset() {
        for(ObjectStore<C> store: storeMap) {
            store.reset();
        }
    }


    public C rent(int typ) {
        ObjectStore<C> store = storeMap.get(typ);
        if(store == null) {
            store = new ObjectStore<C>(() -> factory.createCommand(typ));
            storeMap.set(typ, store);
        }
        return store.rent();
    }

    public void Return(C dmd) {
        ObjectStore<C> store = storeMap.get(dmd.type);
        store.Return(dmd);
        //BayServer.debug(owner + " return packet " + type + " activeCount=" + activeCount);
    }


    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sPacketStore(%s) usage nTypes=%d", StringUtil.indent(indent), protocol, storeMap.size());
        for(int i = 0; i < storeMap.size(); i++) {
            if(storeMap.get(i) != null) {
                BayLog.info("%sType: %d", StringUtil.indent(indent+1), i);
                storeMap.get(i).printUsage(indent+2);
            }
        }
    }

    public static void init() {
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static CommandStore getStore(String protocol, int agentId) {
        return protoMap.get(protocol).stores.get(agentId);
    }

    public static void registerProtocol(
            String protocol,
            CommandFactory cmdFactory) {
        if(!protoMap.containsKey(protocol)) {
            protoMap.put(protocol, new CommandStore.ProtocolInfo(protocol, cmdFactory));
        }
    }

    public static List<CommandStore> getStores(int agentId) {
        List<CommandStore> storeList = new ArrayList<>();
        protoMap.values().forEach(ifo -> {
            storeList.add(ifo.stores.get(agentId));
        });
        return storeList;
    }
}
