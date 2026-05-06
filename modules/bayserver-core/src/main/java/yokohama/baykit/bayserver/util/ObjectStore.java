package yokohama.baykit.bayserver.util;


import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;

import java.util.ArrayDeque;

public class ObjectStore<T extends Reusable> implements Reusable{

    final ArrayDeque<T> freeList = new ArrayDeque<>();
    //final HashSet<T> activeList = new HashSet<>();
    public ObjectFactory<T> factory;

    public ObjectStore(ObjectFactory<T> factory) {
        this.factory = factory;
    }

    public ObjectStore() {
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        /*if (activeList.size() > 0) {
            BayLog.error("BUG?: There are %d active objects: %s", activeList.size(), activeList);
            // for security
            freeList.clear();
            activeList.clear();
        }*/
        freeList.clear();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * rent / Return are concurrent: a single agent's PacketStore is
     * accessed by both the agent's grand-agent thread (= when an inbound
     * packet is read from the network) and arbitrary Taxi threads (=
     * when a Train sends a response). ArrayDeque is not thread-safe,
     * so concurrent {@code freeList.poll()} can race -- two callers
     * pass {@code isEmpty()} and one ends up with {@code null}, then
     * {@code throw new Sink()} fires and the request is broken. The
     * synchronized blocks below make rent/Return atomic w.r.t. the
     * freeList. Fall back to {@code factory.createObject()} on miss
     * so an emptied store keeps serving without throwing.
     */
    public T rent() {
        T obj;
        synchronized (freeList) {
            obj = freeList.poll();
        }
        if(obj == null) {
            obj = factory.createObject();
        }
        if(obj == null)
            throw new Sink();
        return obj;
    }

    public void Return(T obj, boolean reuse) {
        if(reuse) {
            obj.reset();
            synchronized (freeList) {
                freeList.add(obj);
            }
        }
    }

    public void Return(T obj) {
        Return(obj, true);
    }

    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sfree list: %d", StringUtil.indent(indent), freeList.size());
        //BayLog.info("%sactive list: %d", StringUtil.indent(indent), activeList.size());
        if(BayLog.isDebugMode()) {
            //activeList.forEach(obj -> BayLog.debug("%s%s", StringUtil.indent(indent+1), obj));
        }
    }
}
