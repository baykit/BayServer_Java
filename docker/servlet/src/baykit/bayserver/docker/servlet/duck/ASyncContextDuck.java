package baykit.bayserver.docker.servlet.duck;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.train.Train;
import baykit.bayserver.train.TrainRunner;
import baykit.bayserver.docker.servlet.ServletDocker;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public abstract class ASyncContextDuck {

    static class ListenerInfo {
        Object aLis;
        Object req;
        Object res;

        public ListenerInfo(Object aLis, Object req, Object res) {
            this.aLis = aLis;
            this.req = req;
            this.res = res;
        }
    }

    Object req;
    Object res;
    ArrayList<ListenerInfo> listeners = new ArrayList<>();
    long timeout;
    boolean original;
    ServletDocker docker;
    boolean started;

    public ASyncContextDuck(
            Object req,
            Object res,
            boolean original,
            ServletDocker docker) {
        this.req = req;
        this.res = res;
        this.original = original;
        this.docker = docker;
    }

    public Object getRequestObject() {
        return req;
    }

    public Object getResponseObject() {
        return res;
    }

    public final boolean hasOriginalRequestAndResponse() {
        return original;
    }

    public final void dispatch() {
        dispatch(docker.reqHelper.getRequestURI(req));
    }

    public final void dispatch(String path) {
        dispatch(docker.ctx, path);
    }

    public final void dispatch(ServletContextDuck ctx, String path) {
        docker.reqHelper.setAttribute(req, ASYNC_REQUEST_URI(),  docker.reqHelper.getRequestURI(req));
        docker.reqHelper.setAttribute(req, ASYNC_CONTEXT_PATH(), docker.reqHelper.getContextPath(req));
        docker.reqHelper.setAttribute(req, ASYNC_PATH_INFO(),    docker.reqHelper.getPathInfo(req));
        docker.reqHelper.setAttribute(req, ASYNC_SERVLET_PATH(), docker.reqHelper.getServletPath(req));
        docker.reqHelper.setAttribute(req, ASYNC_QUERY_STRING(), docker.reqHelper.getQueryString(req));

        RequestDispatcherDuck d = ctx.getRequestDispatcherDuck(path);
        try {
            d.forwardDuck(req, res);
        } catch (ServletExceptionDuck e) {
            BayLog.error(e.getCause());
        } catch (IOException e) {
            BayLog.error(e);
        }
    }

    public final void complete() {
        for (ListenerInfo lis : listeners) {
            try {
                Object ev;
                if(lis.req == null)
                    ev = docker.duckFactory.newAsyncEvent(this);
                else
                    ev = docker.duckFactory.newAsyncEvent(this, lis.req, lis.res);
                docker.listenerHelper.onAsyncComplete(lis.aLis, ev);
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
        Tour tur = (Tour) docker.reqHelper.getAttribute(req, ServletDocker.ATTR_TOUR);
        int tourId = (Integer)docker.reqHelper.getAttribute(req, ServletDocker.ATTR_TOUR_ID);
        try {
            tur.res.endContent(tourId);
        } catch (IOException e) {
            BayLog.error(e);
        }
    }

    public final void start(Runnable run) {
        for (ListenerInfo lis : listeners) {
            try {
                Object ev;
                if(lis.req == null)
                    ev = docker.duckFactory.newAsyncEvent(this);
                else
                    ev = docker.duckFactory.newAsyncEvent(this, lis.req, lis.res);
                docker.listenerHelper.onStartAsync(lis.aLis, ev);
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
        Tour tur = (Tour) docker.reqHelper.getAttribute(req, ServletDocker.ATTR_TOUR);
        started = true;
        TrainRunner.post(new Train(tur) {
            @Override
            protected void depart() throws HttpException {
                run.run();
            }
        });
    }

    public final void addListenerDuck(Object listener) {
        addListenerDuck(listener, null, null);
    }

    public final void addListenerDuck(Object listener, Object req, Object res) {
        listeners.add(new ListenerInfo(listener, req, res));
    }

    public final <T extends Object> T createListenerDuck(Class<T> aClass) throws ServletExceptionDuck {
        try {
            return aClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new ServletExceptionDuck(e);
        }
    }

    public final void setTimeout(long to) {
        timeout = to;
    }

    public final long getTimeout() {
        return timeout;
    }


    public abstract String ASYNC_REQUEST_URI();

    public abstract String ASYNC_CONTEXT_PATH();

    public abstract String ASYNC_PATH_INFO();

    public abstract String ASYNC_SERVLET_PATH();

    public abstract String ASYNC_QUERY_STRING();
}