package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.util.StringUtil;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TourStore
 *  Pool of Tour object
 */
public class TourStore {

    static class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            while(stores.size() < agentId) {
                stores.add(null);
            }
            stores.set(agentId-1, new TourStore());
        }

        @Override
        public void remove(int agentId) {
            stores.set(agentId-1, null);
        }
    }

    public static final int MAX_TOURS = 128;

    ArrayList<Tour> freeTours = new ArrayList<>();
    Map<Long, Tour> activeTourMap = new ConcurrentHashMap<>();
    public static int maxCount;

    /** stores[agent_id - 1] => TourStore */
    static ArrayList<TourStore> stores = new ArrayList<>();

    public synchronized Tour get(long key) {
        return activeTourMap.get(key);
    }

    public synchronized Tour rent(long key, boolean force) {
        Tour tur = get(key);
        if(tur != null)
            throw new Sink("Tour is active: " + tur);

        if (!freeTours.isEmpty()) {
            //BayLog.debug("rent: key=%d from free tours", key);
            tur = freeTours.remove(freeTours.size() - 1);
        } else {
            //BayLog.debug("rent: key=%d Active tour count: %d", key, activeTourMap.size());
            if (!force && (activeTourMap.size() >= maxCount)) {
                return null;
            } else {
                tur = new Tour();
            }
        }

        activeTourMap.put(key, tur);
        return tur;
    }

    public synchronized void Return(long key) {
        if(!activeTourMap.containsKey(key)) {
            throw new Sink("Tour is not active key=: " + key);
        }
        //BayLog.debug("return: key=%d Active tour count: before=%d", key, activeTourMap.size());
        Tour tur = activeTourMap.remove(key);
        //BayLog.debug("return: key=%d Active tour count: after=%d", key, activeTourMap.size());
        tur.reset();
        freeTours.add(tur);
    }

    /**
     * print memory usage
     */
    public void printUsage(int indent) {
        BayLog.info("%sTour store usage:", StringUtil.indent(indent));
        BayLog.info("%sfreeList: %d", StringUtil.indent(indent+1), freeTours.size());
        BayLog.info("%sactiveList: %d", StringUtil.indent(indent+1), activeTourMap.size());
        if(BayLog.isDebugMode()) {
            activeTourMap.values().forEach(obj -> BayLog.debug("%s%s", StringUtil.indent(indent+1), obj));
        }
    }


    public static void init(int maxTourCount) {
        TourStore.maxCount = maxTourCount;
        GrandAgent.addLifecycleListener(new AgentListener());
    }

    public static TourStore getStore(int agentId) {
        return stores.get(agentId-1);
    }
}
