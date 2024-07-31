package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.util.ObjectStore;
import yokohama.baykit.bayserver.util.StringUtil;

import java.util.ArrayList;

public class WarpShipStore extends ObjectStore<WarpShip> {
    
    final ArrayList<WarpShip> keepList = new ArrayList<>();
    final ArrayList<WarpShip> busyList = new ArrayList<>();

    int maxShips;

    public WarpShipStore(int maxShips) {
        this.maxShips = maxShips;
        factory = (() -> new WarpShip());
    }

    public synchronized WarpShip rent() {
        if(maxShips > 0 && count() >= maxShips)
            return null;
        
        WarpShip wsip;
        if(keepList.isEmpty()) {
            BayLog.trace("rent from Object Store");
            wsip = super.rent();
            if (wsip == null)
                return null;
        }
        else {
            BayLog.trace("rent from freeList: %s", keepList);
            wsip = keepList.remove(keepList.size() - 1);
        }
        if(wsip == null)
            throw new Sink("BUG! warp ship is null");
        busyList.add(wsip);

        if(BayLog.isTraceMode())
            BayLog.trace("rent: freeList=" + keepList + " busyList=" + busyList);
        return wsip;         
    }

    /**
     * Keep ship which connection is alive
     * @param wsip
     */
    public synchronized void keep(WarpShip wsip) {
        BayLog.trace("keep: before freeList=%s busyList=%s", keepList, busyList);

        if(!busyList.remove(wsip))
            BayLog.error("BUG: " + wsip + " not in busy list");
        keepList.add(wsip);
        BayLog.trace("keep: after freeList=%s busyList=%s", keepList, busyList);
    }

    /**
     * Return ship which connection is closed
     * @param wsip
     */
    public synchronized void Return(WarpShip wsip) {
        BayLog.trace("Return: before freeList=%s busyList=%s", keepList, busyList);
        boolean removedFromKeep = keepList.remove(wsip);
        boolean removedFromBusy = busyList.remove(wsip);
        if(!removedFromKeep && !removedFromBusy)
            BayLog.error("BUG:" + wsip + " not in both keep and busy list");

        super.Return(wsip);
        BayLog.trace("Return: after freeList=%s busyList=%s", keepList, busyList);
    }
    
    int count() {
        return keepList.size() + busyList.size();
    }

    int busyCount() {
        return busyList.size();
    }

    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
        BayLog.info("%sWarpShipStore Usage:", StringUtil.indent(indent));
        BayLog.info("%skeepList: %d", StringUtil.indent(indent+1), keepList.size());
        if(BayLog.isDebugMode()) {
            keepList.forEach(obj -> BayLog.debug("%s%s", StringUtil.indent(indent+1), obj));
        }
        BayLog.info("%sbusyList: %d", StringUtil.indent(indent+1), busyList.size());
        if(BayLog.isDebugMode()) {
            busyList.forEach(obj -> BayLog.debug("%s%s", StringUtil.indent(indent+1), obj));
        }
        super.printUsage(indent);
    }
}
