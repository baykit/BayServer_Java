package baykit.bayserver.docker.servlet.duck;

import java.io.IOException;

public interface ListenerHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for ServletContextListener
    //////////////////////////////////////////////////////////////
    Class getContextListenerClass();

    void contextInitialized(Object listener, Object servletContextEv);

    void contextDestroyed(Object listener, Object servletContextEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for ServletContextAttributeListener
    //////////////////////////////////////////////////////////////
    Class contextAttributeListenerClass();

    void contextAttributeReplaced(Object listener, Object servletContextAttributeEv);

    void contextAttributeAdded(Object listener, Object servletContextAttributeEv);

    void contextAttributeRemoved(Object listener, Object servletContextAttributeEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionListener
    //////////////////////////////////////////////////////////////

    Class httpSessionListenerClass();

    void sessionCreated(Object listener, Object httpSessionEv);

    void sessionDestroyed(Object listener, Object httpSessionEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionAttributeListener
    //////////////////////////////////////////////////////////////
    Class httpSessionAttributeListenerClass();

    void sessionAttributeAdded(Object listener, Object httpSessionBindingEv);

    void sessionAttributeRemoved(Object listener, Object httpSessionBindingEv);

    void sessionAttributeReplaced(Object listener, Object httpSessionBindingEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionIdListener
    //////////////////////////////////////////////////////////////

    Class httpSessionIdListenerClass();

    void sessionIdChanged(Object listener, Object httpSessionEv, String oldSessionId);


    //////////////////////////////////////////////////////////////
    // Helper methods for  HttpSessionBindingListener
    //////////////////////////////////////////////////////////////

    Class httpSessionBindingListenerClass();

    void valueUnbound(Object listener, Object httpSessionBindingEv);

    void valueBound(Object listener, Object httpSessionBindingEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for  ServletRequestListener
    //////////////////////////////////////////////////////////////

    Class servletRequestListenerClass();

    void requestInitialized(Object listener, Object servletRequestEv);

    void requestDestroyed(Object listener, Object servletRequestEv);


    //////////////////////////////////////////////////////////////
    // Helper methods for  ServletRequestAttributeListener
    //////////////////////////////////////////////////////////////

    Class servletRequestAttributeListenerClass();

    void requestAttributeAdded(Object listener, Object servletRequestAttributeEv);

    void requestAttributeRemoved(Object listener, Object servletRequestAttributeEv);

    void requestAttributeReplaced(Object listener, Object servletRequestAttributeEv);

    //////////////////////////////////////////////////////////////
    // Helper methods for  AsyncListener
    //////////////////////////////////////////////////////////////

    Class asyncListenerClass();

    void onAsyncComplete(Object listener, Object asyncEv) throws IOException;

    void onAsyncError(Object listener, Object asyncEv) throws IOException;

    void onStartAsync(Object listener, Object asyncEv) throws IOException;

    void onAsyncTimeout(Object listener, Object asyncEv) throws IOException;
}
