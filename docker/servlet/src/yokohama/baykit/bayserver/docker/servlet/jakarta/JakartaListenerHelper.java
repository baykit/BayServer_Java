package yokohama.baykit.bayserver.docker.servlet.jakarta;

import yokohama.baykit.bayserver.docker.servlet.duck.ListenerHelper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;

public class JakartaListenerHelper implements ListenerHelper {

    //////////////////////////////////////////////////////////////
    // Helper methods for ServletContextListener
    //////////////////////////////////////////////////////////////

    @Override
    public Class getContextListenerClass() {
        return ServletContextListener.class;
    }



    @Override
    public void contextInitialized(Object listener, Object servletContextEv) {
        ((ServletContextListener)listener).contextInitialized((ServletContextEvent)servletContextEv);
    }

    @Override
    public void contextDestroyed(Object listener, Object servletContextEv) {
        ((ServletContextListener)listener).contextDestroyed((ServletContextEvent)servletContextEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for ServletContextAttributeListener
    //////////////////////////////////////////////////////////////

    @Override
    public Class contextAttributeListenerClass() {
        return ServletContextAttributeListener.class;
    }

    @Override
    public void contextAttributeAdded(Object listener, Object servletContextAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeAdded((ServletContextAttributeEvent) servletContextAttributeEv);
    }

    @Override
    public void contextAttributeRemoved(Object listener, Object servletContextAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeRemoved((ServletContextAttributeEvent) servletContextAttributeEv);
    }

    @Override
    public void contextAttributeReplaced(Object listener, Object servletContextAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeReplaced((ServletContextAttributeEvent) servletContextAttributeEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionListener
    //////////////////////////////////////////////////////////////
    @Override
    public Class httpSessionListenerClass() {
        return HttpSessionListener.class;
    }

    @Override
    public void sessionCreated(Object listener, Object httpSessionEv) {
        ((HttpSessionListener)listener).sessionCreated((HttpSessionEvent) httpSessionEv);
    }

    @Override
    public void sessionDestroyed(Object listener, Object httpSessionEv) {
        ((HttpSessionListener)listener).sessionDestroyed((HttpSessionEvent) httpSessionEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionAttributeListener
    //////////////////////////////////////////////////////////////
    @Override
    public Class httpSessionAttributeListenerClass() {
        return HttpSessionAttributeListener.class;
    }

    @Override
    public void sessionAttributeAdded(Object listener, Object httpSessionBindingEv) {
        ((HttpSessionAttributeListener)listener).attributeAdded((HttpSessionBindingEvent) httpSessionBindingEv);
    }

    @Override
    public void sessionAttributeRemoved(Object listener, Object httpSessionBindingEv) {
        ((HttpSessionAttributeListener)listener).attributeRemoved((HttpSessionBindingEvent) httpSessionBindingEv);
    }

    @Override
    public void sessionAttributeReplaced(Object listener, Object httpSessionBindingEv) {
        ((HttpSessionAttributeListener)listener).attributeReplaced((HttpSessionBindingEvent) httpSessionBindingEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for HttpSessionIdListener
    //////////////////////////////////////////////////////////////
    @Override
    public Class httpSessionIdListenerClass() {
        return HttpSessionIdListener.class;
    }

    @Override
    public void sessionIdChanged(Object listener, Object httpSessionEv, String oldSessionId) {
        ((HttpSessionIdListener)listener).sessionIdChanged((HttpSessionEvent) httpSessionEv, oldSessionId);
    }


    //////////////////////////////////////////////////////////////
    // Helper methods for  HttpSessionBindingListener
    //////////////////////////////////////////////////////////////
    @Override
    public Class httpSessionBindingListenerClass() {
        return HttpSessionBindingListener.class;
    }

    @Override
    public void valueUnbound(Object listener, Object httpSessionBindingEv) {
        ((HttpSessionBindingListener)listener).valueUnbound((HttpSessionBindingEvent) httpSessionBindingEv);
    }

    @Override
    public void valueBound(Object listener, Object httpSessionBindingEv) {
        ((HttpSessionBindingListener)listener).valueBound((HttpSessionBindingEvent) httpSessionBindingEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for  ServletRequestListener
    //////////////////////////////////////////////////////////////
    @Override
    public Class servletRequestListenerClass() {
        return ServletRequestListener.class;
    }

    @Override
    public void requestInitialized(Object listener, Object servletRequestEv) {
        ((ServletRequestListener)listener).requestInitialized((ServletRequestEvent) servletRequestEv);
    }

    @Override
    public void requestDestroyed(Object listener, Object servletRequestEv) {
        ((ServletRequestListener)listener).requestDestroyed((ServletRequestEvent) servletRequestEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for  ServletRequestAttributeListener
    //////////////////////////////////////////////////////////////

    @Override
    public Class servletRequestAttributeListenerClass() {
        return ServletRequestAttributeListener.class;
    }

    @Override
    public void requestAttributeAdded(Object listener, Object servletRequestAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeAdded((ServletContextAttributeEvent) servletRequestAttributeEv);
    }

    @Override
    public void requestAttributeRemoved(Object listener, Object servletRequestAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeRemoved((ServletContextAttributeEvent) servletRequestAttributeEv);
    }

    @Override
    public void requestAttributeReplaced(Object listener, Object servletRequestAttributeEv) {
        ((ServletContextAttributeListener)listener).attributeReplaced((ServletContextAttributeEvent) servletRequestAttributeEv);
    }

    //////////////////////////////////////////////////////////////
    // Helper methods for  AsyncListener
    //////////////////////////////////////////////////////////////

    @Override
    public Class asyncListenerClass() {
        return AsyncListener.class;
    }

    @Override
    public void onAsyncComplete(Object listener, Object asyncEv) throws IOException {
        ((AsyncListener)listener).onComplete((AsyncEvent) asyncEv);
    }

    @Override
    public void onAsyncError(Object listener, Object asyncEv) throws IOException {
        ((AsyncListener)listener).onError((AsyncEvent) asyncEv);
    }

    @Override
    public void onStartAsync(Object listener, Object asyncEv) throws IOException {
        ((AsyncListener)listener).onStartAsync((AsyncEvent) asyncEv);
    }

    @Override
    public void onAsyncTimeout(Object listener, Object asyncEv) throws IOException {
        ((AsyncListener)listener).onTimeout((AsyncEvent) asyncEv);
    }


}
