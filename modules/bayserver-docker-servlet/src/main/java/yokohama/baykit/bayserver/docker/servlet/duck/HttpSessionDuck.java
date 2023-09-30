package yokohama.baykit.bayserver.docker.servlet.duck;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;

import java.util.*;

/**
 * HttpSession Implementation for duck typing
 */
public class HttpSessionDuck {

    String id;
    long created;
    long last;
    ServletDocker docker;
    int lifeTime;
    HashMap<String, Object> atrs = new HashMap<>();
    boolean sesIsNew;
    ServletHelper helper;
    boolean valid;
    
    public HttpSessionDuck(String id, ServletDocker docker, ServletHelper helper) {
        this.id = id;
        this.docker = docker;
        this.created = System.currentTimeMillis();
        this.lifeTime = docker.sessionStore.sessionLifeTime;
        this.sesIsNew = true;
        this.helper = helper;
        this.valid = true;

        ArrayList<EventListener> listeners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionListenerClass());
        if(!listeners.isEmpty()) {
            Object sesEvt = docker.duckFactory.newHttpSessionEvent(this);
            for (Object lis : listeners) {
                try {
                    docker.listenerHelper.sessionCreated(lis, sesEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////
    public final long getCreationTime() {
        return created;
    }

    public final String getId() {
        return id;
    }

    public final long getLastAccessedTime() {
        return last;
    }

    public final ServletContextDuck getServletContextDuck() {
        return docker.ctx;
    }

    public final void setMaxInactiveInterval(int interval) {
        lifeTime = interval;
    }

    public final int getMaxInactiveInterval() {
        return lifeTime;
    }

    public final HttpSessionContextDuck getSessionContextDuck() {
        return docker.duckFactory.newSessionContext();
    }

    public final Object getAttribute(String name) {
        //BayLog.trace("getAttribute: " + name + " =" + atrs.get(name));
        return atrs.get(name);
    }

    public final Object getValue(String name) {
        return getAttribute(name);
    }

    public final Enumeration getAttributeNames() {
        return Collections.enumeration(atrs.keySet());
    }

    public final String[] getValueNames() {
        return atrs.keySet().toArray(new String[0]);
    }

    public final void setAttribute(String name, Object value) {
        //BayLog.trace("setAttribute: " + name + " =" + value);
        boolean update = atrs.containsKey(name);
        atrs.put(name, value);

        ArrayList<EventListener> atrListeners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionAttributeListenerClass());
        ArrayList<EventListener> bindingListeners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionBindingListenerClass());
        if(!atrListeners.isEmpty() || !bindingListeners.isEmpty()) {
            Object bndEvt = docker.duckFactory.newSessionBindingEvent(this, name, value);
            for (Object listener : atrListeners) {
                try {
                    if (update)
                        docker.listenerHelper.sessionAttributeReplaced(listener, bndEvt);
                    else
                        docker.listenerHelper.sessionAttributeAdded(listener, bndEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
            for(Object listener : bindingListeners) {
                try {
                    docker.listenerHelper.valueBound(listener, bndEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
        }
    }

    public final void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    public final void removeAttribute(String name) {
        atrs.remove(name);

        ArrayList<EventListener> atrListeners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionAttributeListenerClass());
        ArrayList<EventListener> bndListeners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionBindingListenerClass());
        if(!atrListeners.isEmpty() || !bndListeners.isEmpty()) {
            Object bndEvt = docker.duckFactory.newSessionBindingEvent(this, name, null);
            for (Object listener : atrListeners) {
                try {
                    docker.listenerHelper.sessionAttributeRemoved(listener, bndEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }

            for (Object listener : bndListeners) {
                try {
                    docker.listenerHelper.valueUnbound(listener, bndEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
        }
    }

    public final void removeValue(String name) {
        removeAttribute(name);
    }

    public final void invalidate() {
        valid = false;

        ArrayList<EventListener> lilsteners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionListenerClass());
        if(!lilsteners.isEmpty()) {
            Object sesEvt = docker.duckFactory.newHttpSessionEvent(this);
            for (Object lis : lilsteners) {
                try {
                    docker.listenerHelper.sessionDestroyed(lis, sesEvt);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
        }
    }

    public final boolean isNew() {
        BayLog.info("Session: " + id + " isNew=" + sesIsNew);
        return sesIsNew;
    }


    //////////////////////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////////////////////
    public final void update() {
        last = System.currentTimeMillis();
        sesIsNew = false;
    }

    public final boolean isValid() {
        return valid;
    }

    public void chnageId(String newSessionId) {
        String oldId = id;
        id = newSessionId;

        ArrayList<EventListener> lilsteners = docker.listenerStore.getListeners(docker.listenerHelper.httpSessionIdListenerClass());
        if(!lilsteners.isEmpty()) {
            Object sesEvt = docker.duckFactory.newHttpSessionEvent(this);
            for (Object lis : lilsteners) {
                try {
                    docker.listenerHelper.sessionIdChanged(lis, sesEvt, oldId);
                }
                catch(Throwable e) {
                    BayLog.error(e + ": Ignore");
                    BayLog.error(e);
                }
            }
        }
    }
}