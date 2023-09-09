package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;

import java.util.ArrayList;

public class SpinHandler {

    public interface SpinListener {
        NextSocketAction lap(boolean spun[]);
        boolean checkTimeout(int durationSec);
        void close();
    }

    static class ListenerInfo {
        SpinListener listener;
        long lastAccess;

        public ListenerInfo(SpinListener listener, long lastAccess) {
            this.listener = listener;
            this.lastAccess = lastAccess;
        }
    }

    GrandAgent agent;
    int spinCount;

    ArrayList<ListenerInfo> listeners = new ArrayList<>();

    public SpinHandler(GrandAgent agent) {
        this.agent = agent;
    }

    @Override
    public String toString() {
        return agent.toString();
    }

    boolean processData() {
        if (listeners.isEmpty())
            return false;

        boolean allSpun = true;
        ArrayList<Integer> removeList = new ArrayList<>();
        for (int i = listeners.size() - 1; i >= 0; i--) {
            SpinListener lis = listeners.get(i).listener;
            boolean spun[] = new boolean[1];
            NextSocketAction act = lis.lap(spun);

            switch(act) {
                case Suspend:
                    removeList.add(i);
                    break;
                case Close:
                    removeList.add(i);
                    break;
                case Continue:
                    continue;
                default:
                    throw new Sink();
            }

            listeners.get(i).lastAccess = System.currentTimeMillis();
            allSpun = allSpun & spun[0];
        }

        if(allSpun) {
            spinCount++;
            if(spinCount > 10) {
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException e) {
                    BayLog.error(e);
                }
            }
        }
        else {
            spinCount = 0;
        }

        removeList.forEach(i -> {
            synchronized (listeners) {
                listeners.remove(i.intValue());
            }
        });

        return true;
    }

    public synchronized void askToCallBack(SpinListener listener) {
        BayLog.debug("%s Ask to callback: %s", this, listener);
        if(!listeners.stream().anyMatch(ifo -> ifo.listener == listener)) {
            synchronized (listeners) {
                listeners.add(new ListenerInfo(listener, System.currentTimeMillis()));
            }
        }
        else {
            BayLog.error("Already registered");
        }
    }


    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    public void stopTimeoutSpins() {
        if(listeners.isEmpty())
            return;

        ArrayList<Integer> removeList = new ArrayList<>();;
        synchronized (listeners) {
            long now = System.currentTimeMillis();
            for (int i = listeners.size() - 1; i >= 0; i--) {
                ListenerInfo ifo = listeners.get(i);
                if (ifo.listener.checkTimeout((int) (now - ifo.lastAccess) / 1000)) {
                    ifo.listener.close();
                    removeList.add(i);
                }
            }
        }

        for (Integer i : removeList) {
            synchronized (listeners) {
                listeners.remove(i);
            }
        }
    }

}
