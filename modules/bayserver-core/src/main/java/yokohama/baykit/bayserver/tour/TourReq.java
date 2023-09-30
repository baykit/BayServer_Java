package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpUtil;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;

public class TourReq implements Reusable {

    public interface RemoteHostResolver {
        String getRemoteHost();
    }

    public static class DefaultRemoteHostResolver implements RemoteHostResolver{

        final TourReq req;

        public DefaultRemoteHostResolver(TourReq req) {
            this.req = req;
        }

        public String getRemoteHost() {
            if(req.remoteAddress == null)
                return null;
            return HttpUtil.resolveHost(req.remoteAddress);
        }
    }

    private final Tour tour;
    /**
     * Request Header info
     */
    public int key;  // request id in FCGI or stream id in HTTP/2

    public String uri;
    public String protocol;
    public String method;

    public Headers headers = new Headers();

    public String rewrittenURI; // set if URI is rewritten
    public String queryString;
    public String pathInfo;
    public String scriptName;
    public String reqHost;  // from Host header
    public int reqPort;     // from Host header

    public String remoteUser;
    public String remotePass;

    public String remoteAddress;
    public int remotePort;
    public RemoteHostResolver remoteHostFunc;   // remote host is resolved on demand since performance reason

    public String serverAddress;
    public int serverPort;
    public String serverName;
    String charset;

    /**
     * Request content info
     */
    public int bytesPosted;
    public int bytesConsumed;
    public int bytesLimit;
    public ReqContentHandler contentHandler;
    public ContentConsumeListener consumeListener;
    boolean available;
    boolean ended;

    public TourReq(Tour tour) {
        this.tour = tour;
    }


    void init(int key) {
        this.key = key;
    }

    //////////////////////////////////////////////////////////////////
    // Implements Reusable
    //////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        headers.clear();
        key = 0;
        uri = null;
        method = null;
        protocol = null;
        bytesPosted = 0;
        bytesConsumed = 0;
        bytesLimit = 0;

        rewrittenURI = null;
        queryString = null;
        pathInfo = null;
        scriptName = null;
        reqHost = null;
        reqPort = 0;
        remoteUser = null;
        remotePass = null;

        remoteAddress = null;
        remotePort = 0;
        remoteHostFunc = null;
        serverAddress = null;
        serverPort = 0;
        serverName = null;

        charset = null;
        contentHandler = null;
        consumeListener = null;
        available = false;
        ended = false;
    }

    //////////////////////////////////////////////////////////////////
    /// Other methods
    //////////////////////////////////////////////////////////////////

    public String charset() {
        if (StringUtil.empty(charset))
            return null;
        else
            return charset;
    }

    public void setCharset(String charset) {
        this.charset = StringUtil.parseCharset(charset);
    }


    // Remote host are evaluated later because it needs host name lookup
    public String remoteHost() {
        if (remoteHostFunc == null)
            return null;
        else
            return remoteHostFunc.getRemoteHost();
    }

    public synchronized void setContentHandler(ReqContentHandler hnd) {
        if(hnd == null)
            throw new NullPointerException();
        if(contentHandler != null)
            throw new Sink("content handler is already set");

        this.contentHandler = hnd;
    }

    public void setConsumeListener(int limit, ContentConsumeListener listener) {
        if (limit < 0) {
            throw new Sink("Invalid limit");
        }
        this.consumeListener = listener;
        this.bytesLimit = limit;
        this.bytesConsumed = 0;
        this.bytesPosted = 0;
        this.available = true;
    }

    public boolean postContent(int checkId, byte[] data, int start, int len) throws IOException {
        tour.checkTourId(checkId);
        if(!tour.isRunning()) {
            BayLog.debug("%s tour is not running.", tour);
            return true;
        }

        if (tour.req.contentHandler == null) {
            BayLog.warn("%s content read, but no content handler", tour);
            return true;
        }
        if (consumeListener == null) {
            throw new Sink("Request consume listener is null");
        }

        if (bytesPosted + len > bytesLimit) {
            throw new ProtocolException(BayMessage.get(Symbol.HTP_READ_DATA_EXCEEDED, bytesPosted + len,  bytesLimit));
        }

        // If has error, only read content. (Do not call content handler)
        if(tour.error == null)
            contentHandler.onReadContent(tour, data, start, len);
        bytesPosted += len;

        BayLog.debug("%s read content: len=%d posted=%d limit=%d consumed=%d available=%b",
                       tour, len, bytesPosted, bytesLimit, bytesConsumed, available);

        if(tour.error == null)
            return true;

        boolean oldAvailable = available;
        if(!bufferAvailable())
            available = false;
        if(oldAvailable && !available) {
            BayLog.debug("%s request unavailable (_ _).zZZ: posted=%d consumed=%d", this,  bytesPosted, bytesConsumed);
        }

        return available;
    }

    public void endContent(int checkId) throws IOException, HttpException {
        BayLog.debug(tour + " endReqContent");
        tour.checkTourId(checkId);
        if (ended)
            throw new Sink(tour + " Request content is already ended");

        if (bytesLimit >= 0 && bytesPosted != bytesLimit) {
            throw new ProtocolException("nvalid request data length: " + bytesPosted + "/" + bytesLimit);
        }
        if (contentHandler != null)
            contentHandler.onEndContent(tour);
        ended = true;
    }

    public void consumed(int checkId, int length) {
        tour.checkTourId(checkId);
        if (consumeListener == null)
            throw new Sink("Request consume listener is null");

        bytesConsumed += length;
        BayLog.debug("%s reqConsumed: len=%d posted=%d limit=%d consumed=%d available=%b",
                        tour, length, bytesPosted, bytesLimit, bytesConsumed, available);

        boolean resume = false;

        boolean oldAvailable = available;
        if(bufferAvailable())
            available = true;
        if(!oldAvailable && available) {
            BayLog.debug("%s request available (^o^): posted=%d consumed=%d", this,  bytesPosted, bytesConsumed);
            resume = true;
        }

        consumeListener.contentConsumed(length, resume);
    }

    public synchronized boolean abort() {
        if (!tour.isPreparing()) {
            BayLog.debug("%s cannot abort non-preparing tour", tour);
            return false;
        }

        BayLog.debug("%s req abort", tour);
        if (tour.isAborted())
            throw new Sink("tour is already aborted");

        boolean aborted = true;
        if (tour.isRunning() && contentHandler != null)
            aborted = contentHandler.onAbort(tour);

        if(aborted)
            tour.changeState(Tour.TOUR_ID_NOCHECK, Tour.TourState.ABORTED);

        return aborted;
    }


    private boolean bufferAvailable() {
        return bytesPosted - bytesConsumed < BayServer.harbor.tourBufferSize();
    }
}
