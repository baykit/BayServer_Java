package baykit.bayserver.protocol;

import baykit.bayserver.BayLog;
import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.agent.LifecycleListener;
import baykit.bayserver.util.Reusable;
import baykit.bayserver.util.StringUtil;
import baykit.bayserver.util.ObjectStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet pool
 * @param <P> Packet
 * @param <T> Type of packet
 */
public class PacketStore<P extends Packet<T>, T> implements Reusable {

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
        final Map<Integer, PacketStore> stores = new HashMap<>();

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
    final Map<Object, ObjectStore<P>> storeMap = new ConcurrentHashMap<>();
    final PacketFactory<P, T> factory;

    private PacketStore(String protocol, PacketFactory<P, T> factory) {
        this.protocol = protocol;
        this.factory = factory;
    }


    public void reset() {
        for(ObjectStore<P> store: storeMap.values()) {
            store.reset();
        }
    }


    public synchronized P rent(T typ) {
        ObjectStore<P> store = storeMap.get(typ);
        if(store == null) {
            store = new ObjectStore<P>(() -> factory.createPacket(typ));
            storeMap.put(typ, store);
        }
        return store.rent();
    }

    public synchronized void Return(P pkt) {
        ObjectStore<P> store = storeMap.get(pkt.type());
        store.Return(pkt);
        //BayServer.debug(owner + " return packet " + type + " activeCount=" + activeCount);
    }


    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sPacketStore(%s) usage nTypes=%d", StringUtil.indent(indent), protocol, storeMap.size());
        storeMap.keySet().forEach(type -> {
            BayLog.info("%sType: %s", StringUtil.indent(indent+1), type);
            storeMap.get(type).printUsage(indent+2);
        });
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
