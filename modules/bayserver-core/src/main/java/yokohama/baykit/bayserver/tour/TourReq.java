package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpUtil;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TourReq implements Reusable {

    public interface RemoteHostResolver {
        String getRemoteHost();
    }

    public static class DefaultRemoteHostResolver implements RemoteHostResolver{

        final String ip;

        public DefaultRemoteHostResolver(String ip) {
            this.ip = ip;
        }

        public String getRemoteHost() {
            if(ip == null)
                return null;
            return HttpUtil.resolveHost(ip);
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

    public synchronized void setReqContentHandler(ReqContentHandler hnd) {
        if(hnd == null)
            throw new NullPointerException();
        if(contentHandler != null)
            throw new Sink("content handler is already set");

        this.contentHandler = hnd;
    }

    public void setLimit(int limit) {
        if (limit < 0) {
            throw new Sink("Invalid limit");
        }
        this.bytesLimit = limit;
        this.bytesConsumed = 0;
        this.bytesPosted = 0;
        this.available = true;
    }

    /**
     * Parse AUTHORIZATION header
     */
    public void parseAuthorization() {
        String auth = headers.get(Headers.AUTHORIZATION);
        if (!StringUtil.empty(auth)) {
            Pattern ptn = Pattern.compile("Basic (.*)");
            Matcher mch = ptn.matcher(auth);
            if (!mch.matches()) {
                BayLog.debug("Not matched with basic authentication format");
            } else {
                auth = mch.group(1);
                try {
                    auth = new String(Base64.getDecoder().decode(auth));
                    ptn = Pattern.compile("(.*):(.*)");
                    mch = ptn.matcher(auth);
                    if (mch.matches()) {
                        remoteUser = mch.group(1);
                        remotePass = mch.group(2);
                    }
                } catch (Exception e) {
                    BayLog.error(e);
                }
            }
        }
    }

    public void parseHostPort(int defaultPort) {
        reqHost = "";

        String hostPort = headers.get(Headers.X_FORWARDED_HOST);
        if(StringUtil.isSet(hostPort)) {
            headers.remove(Headers.X_FORWARDED_HOST);
            headers.set(Headers.HOST, hostPort);
        }

        hostPort = headers.get(Headers.HOST);
        if(StringUtil.isSet(hostPort)) {
            int pos = hostPort.lastIndexOf(':');
            if(pos == -1) {
                reqHost = hostPort;
                reqPort = defaultPort;
            }
            else {
                reqHost = hostPort.substring(0, pos);
                try {
                    reqPort = Integer.parseInt(hostPort.substring(pos + 1));
                }
                catch(NumberFormatException e) {
                    BayLog.error(e);
                }
            }
        }
    }

    /**
     * This method passes a part of the POST request's content to the ReqContentHandler.
     * Additionally, it reduces the internal buffer space by the size of the data passed
     */
    public boolean postReqContent(int checkId, byte[] data, int start, int len, ContentConsumeListener lis) throws IOException {
        tour.checkTourId(checkId);

        boolean dataPassed = false;

        if(!tour.isRunning()) {
            BayLog.debug("%s tour is not running.", tour);
        }

        else if (tour.req.contentHandler == null) {
            BayLog.warn("%s content read, but no content handler", tour);
        }

        else if (bytesPosted + len > bytesLimit) {
            throw new ProtocolException(BayMessage.get(Symbol.HTP_READ_DATA_EXCEEDED, bytesPosted + len,  bytesLimit));
        }

        // If has error, only read content. (Do not call content handler)
        else if(tour.error != null) {
            BayLog.debug("%s tour has error.", tour);
        }

        else {
            contentHandler.onReadReqContent(tour, data, start, len, lis);
            dataPassed = true;
        }

        bytesPosted += len;
        BayLog.debug("%s read content: len=%d posted=%d limit=%d consumed=%d available=%b",
                       tour, len, bytesPosted, bytesLimit, bytesConsumed, available);

        if(!dataPassed)
            return true;

        boolean oldAvailable = available;
        if(!bufferAvailable())
            available = false;
        if(oldAvailable && !available) {
            BayLog.debug("%s request unavailable (_ _).zZZ: posted=%d consumed=%d", this,  bytesPosted, bytesConsumed);
        }

        return available;
    }

    /**
     * When calling this method, it is uncertain whether the response will be synchronous or asynchronous.
     * If it is synchronous, the tour will be disposed, and no further processing on the tour will be permitted.
     */
    public void endReqContent(int checkId) throws IOException, HttpException {
        BayLog.debug("%s endReqContent", tour);
        tour.checkTourId(checkId);
        if (ended)
            throw new Sink(tour + " Request content is already ended");
        ended = true;

        if (bytesLimit >= 0 && bytesPosted != bytesLimit) {
            throw new ProtocolException("nvalid request data length: " + bytesPosted + "/" + bytesLimit);
        }
        if (contentHandler != null)
            contentHandler.onEndReqContent(tour);
    }

    /**
     * This method is called when the content of a POST request is consumed by the ReqContentHandler.
     * It then increases the internal buffer space by the amount consumed
     */
    public void consumed(int checkId, int length, ContentConsumeListener lis) {
        tour.checkTourId(checkId);

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

        lis.contentConsumed(length, resume);
    }

    public synchronized boolean abort() {
        BayLog.debug("%s req abort", tour);
        if (tour.isPreparing()) {
            tour.changeState(tour.tourId, Tour.TourState.ABORTED);
            return true;
        }
        else if (tour.isRunning()) {
            boolean aborted = true;

            if (contentHandler != null)
                aborted = contentHandler.onAbortReq(tour);

            if (aborted)
                tour.changeState(tour.tourId, Tour.TourState.ABORTED);

            return aborted;
        }
        else {
            BayLog.debug("%s tour is not preparing or not running", tour);
            return false;
        }
    }


    private boolean bufferAvailable() {
        return bytesPosted - bytesConsumed < BayServer.harbor.tourBufferSize();
    }
}
