package baykit.bayserver;

import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.docker.City;
import baykit.bayserver.docker.Port;
import baykit.bayserver.docker.base.PortBase;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.docker.base.InboundShipStore;
import baykit.bayserver.tour.TourStore;
import baykit.bayserver.docker.warp.WarpDocker;
import baykit.bayserver.util.StringUtil;

import java.util.HashMap;
import java.util.Map;


public class MemUsage {

    static class AgentListener implements GrandAgent.GrandAgentLifecycleListener {

        @Override
        public void add(int agentId) {
            memUsages.put(agentId, new MemUsage(agentId));
        }

        @Override
        public void remove(int agentId) {
            memUsages.remove(agentId);
        }
    }

    /** Agent ID => MemUsage */
    static Map<Integer, MemUsage> memUsages = new HashMap<>();

    final int agentId;

    MemUsage(int agentId) {
        this.agentId = agentId;
    }

    public void printUsage(int indent) {
        InboundShipStore.getStore(agentId).printUsage(indent+1);
        ProtocolHandlerStore.getStores(agentId).forEach(store -> store.printUsage(indent+1));
        PacketStore.getStores(agentId).forEach(store -> store.printUsage(indent+1));
        TourStore.getStore(agentId).printUsage(indent+1);
        BayServer.cities.cities().forEach(city -> printCityUsage(null, city, indent));
        BayServer.ports.forEach(port -> {
            ((PortBase)port).cities.cities().forEach(city -> printCityUsage(port, city, indent));
        });
    }

    public static void init() {
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static MemUsage get(int agentId) {
        return memUsages.get(agentId);
    }


    void printCityUsage(Port port, City city, int indent) {
        String pname = port == null ? "" : "@" + port;
        city.clubs().forEach(club -> {
            if (club instanceof WarpDocker) {
                BayLog.info("%sClub(%s%s) Usage:", StringUtil.indent(indent), club, pname);
                ((WarpDocker) club).getShipStore(agentId).printUsage(indent+1);
            }
        });
        city.towns().forEach(town -> {
            town.clubs().forEach(club -> {
                if (club instanceof WarpDocker) {
                    BayLog.info("%sClub(%s%s) Usage:", StringUtil.indent(indent), club, pname);
                    ((WarpDocker) club).getShipStore(agentId).printUsage(indent+1);
                }
            });
        });

    }
}
