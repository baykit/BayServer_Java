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
 * Packet pool
 * @param <P> Packet
 */
public class PacketStore<P extends Packet> implements Reusable {

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
        final PacketFactory packetFactory;

        /** Agent ID => PacketStore */
        final Map<Integer, PacketStore> stores = new HashMap<>(32);

        public ProtocolInfo(String proto, PacketFactory packetFactory) {
            this.protocol = proto;
            this.packetFactory = packetFactory;
        }

        public void addAgent(int agtId) {
            PacketStore store = new PacketStore(protocol, packetFactory);
            stores.put(agtId, store);
        }

        public void removeAgent(int agtId) {
            stores.remove(agtId);
        }
    }

    static Map<String, ProtocolInfo> protoMap = new HashMap<>();

    final String protocol;
    final ArrayList<ObjectStore<P>> storeMap = new ArrayList<>();
    final PacketFactory<P> factory;

    private PacketStore(String protocol, PacketFactory<P> factory) {
        this.protocol = protocol;
        this.factory = factory;
        for(int i = 0; i < 128; i++) {
            storeMap.add(null);
        }
    }


    public void reset() {
        for(ObjectStore<P> store: storeMap) {
            store.reset();
        }
    }


    public synchronized P rent(int typ) {
        ObjectStore<P> store = storeMap.get(typ);
        if(store == null) {
            store = new ObjectStore<P>(() -> factory.createPacket(typ));
            storeMap.set(typ, store);
        }
        return store.rent();
    }

    public synchronized void Return(P pkt) {
        ObjectStore<P> store = storeMap.get(pkt.type);
        store.Return(pkt);
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

    public static PacketStore getStore(String protocol, int agentId) {
        return protoMap.get(protocol).stores.get(agentId);
    }

    public static void registerProtocol(
            String protocol,
            PacketFactory pktFactory) {
        if(!protoMap.containsKey(protocol)) {
            protoMap.put(protocol, new PacketStore.ProtocolInfo(protocol, pktFactory));
        }
    }

    public static List<PacketStore> getStores(int agentId) {
        List<PacketStore> storeList = new ArrayList<>();
        protoMap.values().forEach(ifo -> {
            storeList.add(ifo.stores.get(agentId));
        });
        return storeList;
    }
}
