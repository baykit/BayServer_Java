package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Town;
import yokohama.baykit.bayserver.docker.base.ClubBase;
import yokohama.baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.docker.servlet.jakarta.*;
import yokohama.baykit.bayserver.docker.servlet.javax.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ServletDocker extends ClubBase {

    class ChainBuilder {

        FilterChainDuck chain;
        String pathInfo;
        String servletPath;
        boolean async;

        public void build(Tour tour, String relUri)
                throws ClassNotFoundException, ServletExceptionDuck, InstantiationException, IllegalAccessException, HttpException {
            MappingStore.MatchResult svtResult = servletStore.getServlet(relUri);
            Object svt = null;
            if(svtResult == null) {
                File osPath = new File(ctx.getRealPath(relUri));
                if(osPath.isDirectory()) {
                    if(!relUri.endsWith("/")) {
                        String ctxPath = ctx.getContextPath();
                        if(ctxPath.equals(""))
                            ctxPath = "/";
                        throw new HttpException(HttpStatus.MOVED_PERMANENTLY, ctxPath + relUri.substring(1));
                    }
                    else {
                        // check if welcome file exists
                        for (String wcmName : welcomefiles) {
                            File wcmFile = new File(osPath, wcmName);
                            if (wcmFile.exists()) {
                                String relUrl = relUri + wcmName;
                                svtResult = servletStore.getServlet(relUrl);
                                if(svtResult == null)
                                    svtResult = new MappingStore.MatchResult(fileSvt, ctx.getContextPath(), relUrl, true);
                                break;
                            }
                        }
                    }
                }
                else if(osPath.isFile()){
                    svtResult = new MappingStore.MatchResult(fileSvt, ctx.getContextPath(), relUri, true);
                }
            }

            if(svtResult == null) {
                svtResult = new MappingStore.MatchResult(fileSvt, ctx.getContextPath(), relUri, true);
            }

            async = svtResult.async;

            ArrayList<Object> filters = new ArrayList<>();
            for(MappingStore.MatchResult fltResult : filterStore.getFilter(relUri)) {
                async = async && fltResult.async;
                filters.add(fltResult.matchedObj);
            }

            pathInfo = svtResult.pathInfo;
            servletPath = svtResult.servletPath;
            chain = filterHelper.newFilterChain(filters.iterator(), svtResult.matchedObj);
        }
    }

    public static final String DEFAULT_CHARSET = "iso-8859-1";
    public static final String PARAM_SESID = "bsessid";

    // Include Request Dispatcher attribute
    public String ATTR_REQUEST_URI;
    public String ATTR_CONTEXT_PATH;
    public String ATTR_SERVLET_PATH;
    public String ATTR_PATH_INFO;
    public String ATTR_QUERY_STRING;
    
    // Context attribute
    public String ATTR_TEMP_DIR;

    public String ATTR_ERROR_STATUS_CODE;
    public String ATTR_ERROR_EXCEPTION_TYPE;
    public String ATTR_ERROR_MESSAGE;
    public String ATTR_ERROR_EXCEPTION;
    public String ATTR_ERROR_REQUEST_URI;
    public String ATTR_ERROR_SERVLET_NAME;

    public static final String ATTR_TOUR = "BayServer.tour";
    public static final String ATTR_TOUR_ID = "BayServer.tourId";
    public static final String ATTR_ERR_PAGE = "BayServer.errorPage";
    public WebXml webXml;
    public AnnotationScanner annScanner;
    public String tempDir;
    public Town town;

    public DuckFactory duckFactory;
    public ServletHelper servletHelper;
    public FilterHelper filterHelper;
    public HttpServletRequestHelper reqHelper;
    public HttpServletResponseHelper resHelper;
    public CookieHelper cookieHelper;
    public AnnotationHelper annotationHelper;
    public ListenerHelper listenerHelper;
    public ServletContextDuck ctx;

    public ResourceDocker res;
    public FilterStore filterStore = new FilterStore();
    public ServletStore servletStore = new ServletStore();
    public ListenerStore listenerStore = new ListenerStore();
    public ResorceRefStore resorceRefStore = new ResorceRefStore();
    public HashMap<String, String> ctxParams = new HashMap<>();
    public ArrayList<String> welcomefiles = new ArrayList<>();
    public SessionStore sessionStore = new SessionStore(this);
    public ErrorPageStore errorPageStore = new ErrorPageStore();

    public JasperSupport jasperSupport;

    static final ThreadLocal<ClassLoader> storedContextClassLoader = new ThreadLocal<>();

    
    public Object fileSvt;

    String apiType = null;
    boolean scanAnn = true;
    String reqEncoding = null, resEncoding = null;
    int timeout = 0;
    boolean preload = false;


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
        if(ServletMessage.msg == null) {
            ServletMessage.init();
        }

        town = (Town)parent;

        if(StringUtil.empty(tempDir)) {
            tempDir = BayServer.bservHome + "/tmp";
        }
        new File(tempDir).mkdirs();
        
        if("jakarta".equals(apiType)) {
            duckFactory = new JakartaDuckFactory(this);
            servletHelper = new JakartaServletHelper();
            cookieHelper = new JakartaCookieHelper();
            annotationHelper = new JakartaAnnotationHelper();
            listenerHelper = new JakartaListenerHelper();
            filterHelper = new JakartaFilterHelper(this);
            reqHelper = new JakartaHttpServletRequestHelper();
            resHelper = new JakartaHttpServletResponseHelper();
            ATTR_REQUEST_URI = "jakarta.servlet.include.request_uri";
            ATTR_CONTEXT_PATH = "jakarta.servlet.include.context_path";
            ATTR_SERVLET_PATH = "jakarta.servlet.include.servlet_path";
            ATTR_PATH_INFO = "jakarta.servlet.include.path_info";
            ATTR_QUERY_STRING = "jakarta.servlet.include.query_string";
            ATTR_ERROR_STATUS_CODE = "jakarta.servlet.error.status_code";
            ATTR_ERROR_EXCEPTION_TYPE = "jakarta.servlet.error.exception_type";
            ATTR_ERROR_MESSAGE = "jakarta.servlet.error.message";
            ATTR_ERROR_EXCEPTION = "jakarta.servlet.error.exception";
            ATTR_ERROR_REQUEST_URI = "jakarta.servlet.error.request_uri";
            ATTR_ERROR_SERVLET_NAME = "jakarta.servlet.error.servlet_name";
            ATTR_TEMP_DIR = "jakarta.servlet.context.tempdir";
        }
        else {
            duckFactory = new JavaxDuckFactory(this);
            servletHelper = new JavaxServletHelper();
            cookieHelper = new JavaxCookieHelper();
            annotationHelper = new JavaxAnnotationHelper();
            listenerHelper = new JavaxListenerHelper();
            filterHelper = new JavaxFilterHelper(this);
            reqHelper = new JavaxHttpServletRequestHelper();
            resHelper = new JavaxHttpServletResponseHelper();
            ATTR_REQUEST_URI = "javax.servlet.include.request_uri";
            ATTR_CONTEXT_PATH = "javax.servlet.include.context_path";
            ATTR_SERVLET_PATH = "javax.servlet.include.servlet_path";
            ATTR_PATH_INFO = "javax.servlet.include.path_info";
            ATTR_QUERY_STRING = "javax.servlet.include.query_string";
            ATTR_ERROR_STATUS_CODE = "javax.servlet.error.status_code";
            ATTR_ERROR_EXCEPTION_TYPE = "javax.servlet.error.exception_type";
            ATTR_ERROR_MESSAGE = "javax.servlet.error.message";
            ATTR_ERROR_EXCEPTION = "javax.servlet.error.exception";
            ATTR_ERROR_REQUEST_URI = "javax.servlet.error.request_uri";
            ATTR_ERROR_SERVLET_NAME = "javax.servlet.error.servlet_name";
            ATTR_TEMP_DIR = "javax.servlet.context.tempdir";
        }

        String ctxPath = town.name();
        if(!ctxPath.equals("") && ctxPath.endsWith("/"))
            ctxPath = ctxPath.substring(0, ctxPath.length() - 1);


        ctx = duckFactory.newContext("context(" + ctxPath + ")", ctxPath, town.location(), this);
        ctx.reqEncoding = reqEncoding;
        ctx.resEncoding = resEncoding;
        ctx.timeout = timeout;

        webXml = new WebXml(this);
        annScanner = new AnnotationScanner(this);
        if(scanAnn)
            annScanner.scanAnnotations();

        File webXmlFile = new File(town.location(), "WEB-INF/web.xml");
        if(webXmlFile.exists()) {
            try {
                webXml.parseXml(webXmlFile);
            } catch (Exception e) {
                throw new ConfigException(
                        elm.fileName,
                        elm.lineNo,
                        ServletMessage.get(ServletSymbol.SVT_WEBXML_PARSE_ERROR, e),
                        e);
            }
        }

        fileSvt = duckFactory.newFileServlet();
        try {
            servletHelper.initServlet(fileSvt, duckFactory.newServletConfig(ctx, new HashMap<>(), "fileServlet"));
        } catch (ServletExceptionDuck e) {
            // never thrown exception because init method of file servlet is empty
            BayLog.error(e);
        }

        if(preload) {
            setContextLoader();
            try {
                ctx.init();
            } catch (Exception e) {
                throw new ConfigException(
                        elm.fileName,
                        elm.lineNo,
                        ServletMessage.get(ServletSymbol.SVT_CONTEXT_INITIALIZE_ERROR, e),
                        e);
            }
            restoreContextLoader();
        }
    }

    @Override
    public boolean initDocker(Docker dkr) throws ConfigException {
        if (dkr.type().equalsIgnoreCase("resource")) {
            res = (ResourceDocker)dkr;
            return true;
        }
        else {
            return super.initDocker(dkr);
        }
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            default:
                super.initKeyVal(kv);

            case "apitype":
                apiType = kv.value;
                break;

            case "tempdir":
                tempDir = kv.value;
                break;

            case "jasperversion": {
                BigDecimal version = new BigDecimal(kv.value);
                jasperSupport = new JasperSupport(version);
                break;
            }

            case "scanannotations":
                scanAnn = StringUtil.parseBool(kv.value);
                break;

            case "reqencoding":
                reqEncoding = kv.value;
                break;

            case "resencoding":
                resEncoding = kv.value;
                break;

            case "timeout":
                timeout = Integer.parseInt(kv.value);
                break;

            case "preload":
                preload = StringUtil.parseBool(kv.value);
                break;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements Club
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void arrive(Tour tour) throws HttpException {

        if (tour.req.uri.contains("..")) {
            throw new HttpException(HttpStatus.FORBIDDEN, tour.req.uri);
        }

        int tourId = tour.tourId;;


        setContextLoader();
        try {

            if(!ctx.initialized())
                ctx.init();

            ReqInfo reqInfo = new ReqInfo();
            analyzeReqHeaders(tour, reqInfo);
            checkSession(reqInfo);

            ChainBuilder cb = new ChainBuilder();
            cb.build(tour, reqInfo.relUri);
            tour.res.headers.setContentType("text/html");

            reqInfo.pathInfo = cb.pathInfo;
            reqInfo.servletPath = cb.servletPath;
            HttpServletResponseDuck res = duckFactory.newResponse(tour, ServletDocker.this);
            HttpServletRequestDuck req = duckFactory.newRequest(tour, reqInfo, res, ServletDocker.this, cb.async);
            res.setRequest(req);
            req.setAttribute(ATTR_TOUR, tour);
            req.setAttribute(ATTR_TOUR_ID, tourId);

            if(tour.req.scriptName != null) {
                int pos = tour.req.scriptName.lastIndexOf('.');
                if (pos > 0) {
                    String ext = tour.req.scriptName.substring(pos + 1);
                    String mtype = Mimes.getType(ext);
                    if (mtype != null)
                        res.setContentType(mtype);
                }
            }
            ServletTrain train = new ServletTrain(this, tour, req, res, cb.chain);
            tour.req.setReqContentHandler(train);
            if(cb.async) {
                train.depart();
            }
            else {
                // If sync mode run servlet on another thread
                req.setAsyncSupported(true);
                TrainRunner.post(tour.ship.agentId, train);
            }
        }
        catch (HttpException e) {
            throw e;
        }
        catch (ServletExceptionDuck e) {
            BayLog.error(e.getServletException());
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.getServletException().getMessage());
        }
        catch (Throwable e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
        } finally {
            restoreContextLoader();
        }

    }
    
    ///////////////////////////////////////////////////////////////
    // Other public methods
    ///////////////////////////////////////////////////////////////
    public void setContextLoader() {
        storedContextClassLoader.set(Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(ctx.getClassLoader());
    }

    public void restoreContextLoader() {
        ClassLoader stored = storedContextClassLoader.get();
        Thread.currentThread().setContextClassLoader(stored);

    }
    ///////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////
    /**
     * Analyze request headers.
     * Determine charset, server host, server port, query string
     * @param tur
     * @param reqInfo
     */
    private void analyzeReqHeaders(Tour tur, ReqInfo reqInfo) {
        String ctype = tur.req.headers.contentType();
        if(ctype != null) {
            int pos = ctype.indexOf("charset=");
            if(pos >= 0) {
                tur.req.setCharset(ctype.substring(pos+8).trim());
            }
        }

        String host = tur.req.headers.get(Headers.HOST);
        if (host != null) {
            int pos = host.indexOf(':');
            if (pos != -1) {
                reqInfo.host = host.substring(0, pos);
                reqInfo.port = Integer.parseInt(host.substring(pos + 1));
            } else {
                reqInfo.host = host;
                reqInfo.port = 80;
            }
        }
        reqInfo.scheme = tur.isSecure ? "https" : "http";
        reqInfo.reqUri = tur.req.uri;

        String ctxPath = ctx.getContextPath();
        if(!ctxPath.endsWith("/"))
            ctxPath += "/";

        int pos = tur.req.uri.indexOf('?');
        if(pos >= 0) {
            reqInfo.queryString = tur.req.uri.substring(pos + 1);
            reqInfo.relUri = tur.req.uri.substring(ctxPath.length() - 1, pos);
        }
        else {
            reqInfo.queryString = null;
            reqInfo.relUri = tur.req.uri.substring(ctxPath.length() - 1);
        }

        HashMap<String, String> cookies = new HashMap<>();
        for(String ckeVal : tur.req.headers.headerValues("Cookie")) {

            if (ckeVal != null) {
                KeyValListParser pp = new KeyValListParser(';', '=');
                ArrayList<KeyVal> parsed = pp.parse(ckeVal);

                for (KeyVal nv : parsed) {
                    if(!nv.name.equalsIgnoreCase("path") && !nv.name.equalsIgnoreCase("expires")) {
                        if(!checkCookieItem(nv.name)) {
                            BayLog.warn("Invalid cookie name: " + nv.name);
                            continue;
                        }
                        else if(!checkCookieItem(nv.value)) {
                            BayLog.warn("Invalid cookie value: " + nv.value + "(name=" + nv.name + ")");
                            continue;
                        }
                    }
                    cookies.put(nv.name, nv.value);
                }
            }
        }

        int index = 0;
        reqInfo.cookies = cookieHelper.newArray(cookies.size());
        Iterator<Map.Entry<String, String>> it = cookies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            String name = entry.getKey();
            String value = entry.getValue();
            reqInfo.cookies[index] = cookieHelper.newCookie(name.trim(), value);
            index++;
        }
    }


    /**
     * Check Http session
     * @param reqInfo
     */
    private void checkSession(ReqInfo reqInfo) {
        // session check
        Parameters params = new Parameters();
        params.parse(reqInfo.queryString, null, true);
        String sesId = null;
        if(params.paramMap.containsKey(PARAM_SESID)) {
            sesId = params.paramMap.get(PARAM_SESID)[0];
            reqInfo.cookieSession = false;
        }
        else {
            for (Object c : reqInfo.cookies) {
                if (cookieHelper.getName(c).equals(PARAM_SESID)) {
                    sesId = cookieHelper.getValue(c);
                    reqInfo.cookieSession = true;
                }
            }
        }
        if(sesId != null)
            reqInfo.session = sessionStore.getSession(sesId);
    }


    private static boolean checkCookieItem(String item) {
        char QUOTE_CHAR = '"';
        boolean quoted = false;
        for(int i = 0; i < item.length(); i++) {
            char c = item.charAt(i);
            if (c == QUOTE_CHAR) {
                quoted = !quoted;
                continue;
            }
            else if(quoted) {
                continue;
            }
            else {
                if (c < 0x21)
                    return false;
                else {
                    switch (c) {
                        case '\'':
                        case ',':
                        case ';':
                        case '/':
                        case 0x7F:
                            return false;
                        default:
                    }
                }
            }
        }
        return true;
    }
}
