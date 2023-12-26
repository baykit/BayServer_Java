package yokohama.baykit.bayserver.util;


import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.common.Reusable;

import java.util.ArrayList;

public class ObjectStore<T extends Reusable> implements Reusable{

    final ArrayList<T> freeList = new ArrayList<>();
    final ArrayList<T> activeList = new ArrayList<>();
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
        if (activeList.size() > 0) {
            BayLog.error("BUG?: There are %d active objects: %s", activeList.size(), activeList);
            // for security
            freeList.clear();
            activeList.clear();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////

    public synchronized T rent() {
        T obj;
        //BayLog.debug(owner + " rent freeList=" + freeList);
        if(freeList.isEmpty()) {
            obj = factory.createObject();
        }
        else {
            obj = freeList.remove(freeList.size() - 1);
        }
        if(obj == null)
            throw new Sink();
        activeList.add(obj);
        //BayLog.debug(owner + " rent object " + obj);
        return obj;
    }

    public synchronized void Return(T obj, boolean reuse) {
        //BayLog.debug(owner + " return object " + obj);
        if(freeList.contains(obj))
            throw new Sink("This object already returned: " + obj);

        if(!activeList.contains(obj))
            throw new Sink("This object is not active: " + obj);

        activeList.remove(obj);
        if(reuse) {
            freeList.add(obj);
            obj.reset();
        }
    }

    public synchronized void Return(T obj) {
        Return(obj, true);
    }

    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sfree list: %d", StringUtil.indent(indent), freeList.size());
        BayLog.info("%sactive list: %d", StringUtil.indent(indent), activeList.size());
        if(BayLog.isDebugMode()) {
            activeList.forEach(obj -> BayLog.debug("%s%s", StringUtil.indent(indent+1), obj));
        }
    }
}
