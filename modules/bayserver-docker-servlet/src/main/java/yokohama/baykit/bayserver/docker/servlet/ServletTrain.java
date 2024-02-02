package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.EventListener;

class ServletTrain extends Train implements ReqContentHandler {

    private final ServletDocker docker;
    final Tour tour;
    final HttpServletRequestDuck req;
    final HttpServletResponseDuck res;
    final FilterChainDuck chain;
    final PipedOutputStream pipeOut;
    final PipedInputStream pipeIn;

    public ServletTrain(ServletDocker docker, Tour tour, HttpServletRequestDuck req, HttpServletResponseDuck res, FilterChainDuck chain) {
        this.docker = docker;
        this.tour = tour;
        this.req = req;
        this.res = res;
        this.chain = chain;
        this.pipeOut = new PipedOutputStream();
        try {
            this.pipeIn = new PipedInputStream(pipeOut, BayServer.harbor.tourBufferSize());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        req.setInputStream(pipeIn);
    }

    @Override
    public void depart() {

        docker.setContextLoader();
        BayLog.debug(tour + " Run chain asyncSupported=" + req.isAsyncSupported() + " on " + Thread.currentThread());
        try {
            try {
                chain.doFilter(req, res);
            } catch (ServletExceptionDuck e) {
                RequestDispatcherDuck d = null;
                Throwable se = e.getServletException();
                String errorPageLoc = docker.errorPageStore.find(se.getClass());
                if (errorPageLoc == null && se.getCause() != null) {
                    errorPageLoc = docker.errorPageStore.find(se.getCause().getClass());
                }

                if (errorPageLoc != null) {
                    d = req.getRequestDispatcherDuck(errorPageLoc);
                }

                if (d == null) {
                    throw se;
                } else {
                    BayLog.debug(tour.ship + " Forward to error page");
                    try {
                        req.setAttribute(ServletDocker.ATTR_ERR_PAGE, errorPageLoc);
                        d.forwardDuck(req, res);
                    } catch (ServletExceptionDuck ee) {
                        throw ee.getServletException();
                    } finally {
                        req.removeAttribute(ServletDocker.ATTR_ERR_PAGE);
                    }
                }
            }

            boolean asyncSupported = req.isAsyncSupported();
            boolean asyncStarted = req.isAsyncStarted();

            try {
                if (!asyncSupported || !asyncStarted) {
                    if(!tour.res.headerSent())
                        tour.res.sendHeaders(tour.tourId);
                    tour.res.endResContent(tour.tourId);
                }
            } catch (Throwable ex) {
                BayLog.error(ex);
            }

            BayLog.debug(tour.ship + " End chain");
        } catch (Throwable e) {
            try {
                tour.res.sendError(tour.tourId, HttpStatus.INTERNAL_SERVER_ERROR, null, e);
            } catch (IOException ex) {
                BayLog.error(ex);
            }
        } finally {
            HttpSessionDuck ses = req.getSessionDuck(false);
            if (ses != null)
                ses.update();

            try {
                if (!tour.res.headerSent()) {
                    //tour.sendHeaders();
                } else {
                    if (res.useStream()) {
                        res.getOutputStreamObject().flush();
                    } else if (res.useWriter()) {
                        res.getWriter().flush();
                    }
                }
            } catch (Throwable e) {
                BayLog.error(e);
            }

            // Invoke event handlers
            ArrayList<EventListener> listeners = docker.listenerStore.getListeners(docker.listenerHelper.servletRequestListenerClass());
            if (!listeners.isEmpty()) {
                Object atrEvt = docker.duckFactory.newServletRequestEvent(docker.ctx, req);
                for (Object listener : listeners) {
                    docker.listenerHelper.requestAttributeAdded(listener, atrEvt);
                }
            }

            docker.restoreContextLoader();
        }
    }

    @Override
    protected void onTimer() {

    }

    ///////////////////////////////////////////////////////////////////
    // implements Tour.ContentHandler
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) {
        BayLog.debug(tur + " Servlet:onReadReqContent: len=" + len);

        try {
            pipeOut.write(buf, start, len);
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
        tur.req.consumed(Tour.TOUR_ID_NOCHECK, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) {
        BayLog.debug(tur + " Servlet:endReqContent");
        try {
            pipeOut.close();
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug(tur + " Servlet:abort");
        try {
            pipeOut.close();
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
        return false; // not aborted immediately
    }
}

