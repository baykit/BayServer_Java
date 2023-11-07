package yokohama.baykit.bayserver.docker.base;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.Trouble;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.tour.TourStore;
import yokohama.baykit.bayserver.util.*;
import yokohama.baykit.bayserver.watercraft.Ship;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.util.*;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;


/**
 * Handles TCP/IP connection
 */
public class InboundShip extends Ship {

    protected Port portDkr;

    static Counter errCounter = new Counter();
    boolean needEnd;
    protected int socketTimeoutSec;

    TourStore tourStore;
    List<Tour> activeTours = new ArrayList<>();

    public void initInbound(
            SelectableChannel ch,
            GrandAgent agt,
            Postman pm,
            Port portDkr,
            ProtocolHandler protoHandler) {
        super.init(ch, agt, pm);
        this.portDkr = portDkr;
        this.socketTimeoutSec = portDkr.timeoutSec() >= 0 ? portDkr.timeoutSec() : BayServer.harbor.socketTimeoutSec();
        this.tourStore = TourStore.getStore(agt.agentId);
        setProtocolHandler(protoHandler);
    }

    @Override
    public String toString() {
        return agent + " ship#" + shipId + "/" + objectId + "[" + protocol() + "]";
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public synchronized void reset() {
        super.reset();
        synchronized (this) {
            if (!activeTours.isEmpty()) {
                throw new Sink("%s There are some running tours", this);
            }
        }
        needEnd = false;
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////
    public Port portDocker() {
        return portDkr;
    }

    public Tour getTour(int turKey) {
        return getTour(turKey, false);
    }

    public Tour getTour(int turKey, boolean force) {
        return getTour(turKey, force, true);
    }

    public Tour getTour(int turKey, boolean force, boolean rent) {
        synchronized (tourStore) {
            long storeKey = uniqKey(shipId, turKey);
            Tour tur = tourStore.get(storeKey);
            if (tur == null && rent) {
                tur = tourStore.rent(storeKey, force);
                if(tur == null)
                    return null;
                tur.init(turKey, this);
                activeTours.add(tur);
            }
            if(tur.ship != this)
                throw new Sink();
            tur.checkTourId(tur.id());
            return tur;
        }
    }


    public Tour getErrorTour() {
        int turKey = errCounter.next();
        long storeKey = uniqKey(shipId, -turKey);
        Tour tur = tourStore.rent(storeKey,true);
        tur.init(-turKey, this);
        activeTours.add(tur);
        return tur;
    }

    public void sendHeaders(int chkId, Tour tur) throws IOException {
        checkShipId(chkId);

        if(tur.isZombie() || tur.isAborted()) {
            // Don't send peer any data
            return;
        }

        boolean handled = false;
        if(!tur.errorHandling && tur.res.headers.status() >= 400) {
            Trouble trb = BayServer.harbor.trouble();
            if(trb != null) {
                Trouble.Command cmd = trb.find(tur.res.headers.status());
                if (cmd != null) {
                    Tour errTour = getErrorTour();
                    errTour.req.uri = cmd.target;
                    tur.req.headers.copyTo(errTour.req.headers);
                    tur.res.headers.copyTo(errTour.res.headers);
                    errTour.req.remotePort = tur.req.remotePort;
                    errTour.req.remoteAddress = tur.req.remoteAddress;
                    errTour.req.serverAddress = tur.req.serverAddress;
                    errTour.req.serverPort = tur.req.serverPort;
                    errTour.req.serverName = tur.req.serverName;
                    errTour.res.headerSent = tur.res.headerSent;
                    tur.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ZOMBIE);
                    switch (cmd.method) {
                        case GUIDE: {
                            try {
                                errTour.go();
                            } catch (HttpException e) {
                                throw new IOException(e);
                            }
                            break;
                        }

                        case TEXT: {
                            ((InboundHandler)protocolHandler).sendResHeaders(errTour);
                            byte[] data = cmd.target.getBytes();
                            errTour.res.sendContent(Tour.TOUR_ID_NOCHECK, data, 0, data.length);
                            errTour.res.endContent(Tour.TOUR_ID_NOCHECK);
                            break;
                        }

                        case REROUTE: {
                            errTour.res.sendHttpException(Tour.TOUR_ID_NOCHECK, HttpException.movedTemp(cmd.target));
                            break;
                        }
                    }
                    handled = true;
                }
            }
        }
        if(!handled) {
            for(String[] nv: portDkr.additionalHeaders()) {
                tur.res.headers.add(nv[0], nv[1]);
            }
            ((InboundHandler) protocolHandler).sendResHeaders(tur);
        }
    }

    public void sendRedirect(int chkId, Tour tour, int status, String location) throws IOException {
        checkShipId(chkId);

        Headers hdr = tour.res.headers;
        hdr.setStatus(status);
        hdr.set(Headers.LOCATION, location);

        String body = "<H2>Document Moved.</H2><BR>" + "<A HREF=\""
                + location + "\">" + location + "</A>";

        sendErrorContent(chkId, tour, body);
    }

    public void sendResContent(int chkId, Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException {
        checkShipId(chkId);

        int maxLen = protocolHandler.maxResPacketDataSize();
        if(len > maxLen) {
            sendResContent(Tour.TOUR_ID_NOCHECK, tur, bytes, ofs, maxLen, null);
            sendResContent(Tour.TOUR_ID_NOCHECK, tur, bytes, ofs + maxLen, len - maxLen, lis);
        }
        else {
            ((InboundHandler) protocolHandler).sendResContent(tur, bytes, ofs, len, lis);
        }
    }

    public synchronized void sendEndTour(int chkShipId, int chkTourId, Tour tur, DataConsumeListener lis) throws IOException {
        checkShipId(chkShipId);

        BayLog.debug("%s sendEndTour: %s state=%s", this, tur, tur.state);

        if(!tur.isValid()) {
            throw new Sink("Tour is not valid");
        }
        boolean keepAlive = false;
        if (tur.req.headers.getConnection() == Headers.ConnectionType.KeepAlive)
            keepAlive = true;
        if(keepAlive) {
            Headers.ConnectionType resConn = tur.res.headers.getConnection();
            keepAlive = (resConn == Headers.ConnectionType.KeepAlive)
                    || (resConn == Headers.ConnectionType.Unknown);
            if (keepAlive) {
                if (tur.res.headers.contentLength() < 0)
                    keepAlive = false;
            }
        }

        ((InboundHandler)protocolHandler).sendEndTour(tur, keepAlive, lis);
    }

    public void sendError(int chkId, Tour tour, int status, String message, Throwable e)
            throws IOException {

        checkShipId(chkId);

        if(tour == null)
            throw new NullPointerException();

        BayLog.debug("%s send error: status=%d, message=%s ex=%s", this, status, message, e == null ? "" : e.getMessage(), e);
        if (e != null)
            BayLog.debug(e);

        StringBuilder body = new StringBuilder();

        // Create body
        String str = HttpStatus.description(status);

        // print status
        body.append("<h1>").append(status).append(" ").append(str).append("</h1>").append(Constants.CRLF);

        // print message
	/*
        if (message != null && BayLog.isDebugMode()) {
            body.append(message);
        }
	*/

        // print stack trace
	/*
        if (e != null && BayLog.isDebugMode()) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true);
            e.printStackTrace(pw);

            String stackTrace = sw.toString();

            body.append("<P><HR><P>");
            body.append(Constants.CRLF);
            body.append("<pre>");
            body.append(Constants.CRLF);
            body.append(stackTrace);
            body.append(Constants.CRLF);
            body.append("</pre>");
        }
	*/

        tour.res.headers.setStatus(status);
        sendErrorContent(chkId, tour, body.toString());
    }




    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Custom methods
    ///////////////////////////////////////////////////////////////////////////////////////////////

    protected void sendErrorContent(int chkId, Tour tur, String content)
            throws IOException {

        // Get charset
        String charset = tur.res.charset();

        // Set content type
        if (charset != null && !charset.equals("")) {
            tur.res.headers.setContentType("text/html; charset=" + charset);
        } else {
            tur.res.headers.setContentType("text/html");
        }

        byte[] bytes = null;
        if (content != null && !content.equals("")) {
            // Create writer
            if (charset != null && !charset.equals("")) {
                bytes = content.getBytes(charset);
            } else {
                bytes = content.getBytes();
            }
            tur.res.headers.setContentLength(bytes.length);
        }
        sendHeaders(chkId, tur);

        if (bytes != null)
            sendResContent(chkId, tur, bytes, 0, bytes.length, null);

        //ship.tourEnded();
    }

    void endShip() {
        BayLog.debug("%s endShip", this);
        portDkr.returnProtocolHandler(agent, protocolHandler);
        portDkr.returnShip(this);
    }

    public void abortTours() {
        ArrayList<Tour> returnList = new ArrayList<>();

        // Abort tours
        for(Tour tur: activeTours) {
            if(tur.isValid()) {
                BayLog.debug("%s is valid, abort it: stat=%s", tur, tur.state);
                if(tur.req.abort()) {
                    returnList.add(tur);
                }
            }
        }

        for(Tour tur: returnList) {
            returnTour(tur);
        }
    }

    private static long uniqKey(int sipId, int turKey) {
        return ((long)sipId) << 32 | turKey;
    }

    public void returnTour(Tour tur) {
        BayLog.debug("%s Return tour: %s", this, tur);

        synchronized (tourStore) {
            if (!activeTours.contains(tur))
                throw new Sink("Tour is not in acive list: %s", tur);
            tourStore.Return(uniqKey(shipId, tur.req.key));
            activeTours.remove(tur);

            if (needEnd && activeTours.isEmpty()) {
                endShip();
            }
        }
    }
}

