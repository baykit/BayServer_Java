package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayLog;

import java.util.ArrayList;
import java.util.EventListener;

public class ListenerStore {

    public static class ListenerDesc {
        String clsName;
        EventListener listener;

        public ListenerDesc(String clsName, EventListener listener) {
            this.clsName = clsName;
            this.listener = listener;
        }

        public EventListener getListener()
                throws ClassNotFoundException, InstantiationException, IllegalAccessException {
            if(listener == null) {
                listener = (EventListener) Thread.currentThread().getContextClassLoader().loadClass(clsName).newInstance();
                //listener = (EventListener) Class.forName(clsName).newInstance();
            }
            return listener;
        }
    }

    ArrayList<ListenerDesc> listeners = new ArrayList<>();


    public ArrayList<EventListener> getListeners(Class lnrClass) {
        ArrayList<EventListener> ret = new ArrayList<>();
        for(ListenerStore.ListenerDesc dsc : listeners) {
            EventListener lis = null;
            try {
                lis = dsc.getListener();
                if(lnrClass.isInstance(lis)) {
                    ret.add(lis);
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                BayLog.error(e);
            }
        }
        return ret;
    }

    public void addListener(String className, EventListener lis) {
        listeners.add(new ListenerStore.ListenerDesc(className, lis));
    }
}
