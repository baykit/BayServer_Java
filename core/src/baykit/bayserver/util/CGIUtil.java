package baykit.bayserver.util;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.tour.Tour;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class CGIUtil {

    public interface AddListener {
        void add(String name, String value);
    }
    
    public static final String REQUEST_METHOD = "REQUEST_METHOD";
    public static final String REQUEST_URI = "REQUEST_URI";
    public static final String SERVER_PROTOCOL = "SERVER_PROTOCOL";
    public static final String GATEWAY_INTERFACE = "GATEWAY_INTERFACE";
    public static final String SERVER_NAME = "SERVER_NAME";
    public static final String SERVER_PORT = "SERVER_PORT";
    public static final String QUERY_STRING = "QUERY_STRING";
    public static final String SCRIPT_NAME = "SCRIPT_NAME";
    public static final String SCRIPT_FILENAME = "SCRIPT_FILENAME";
    public static final String PATH_TRANSLATED = "PATH_TRANSLATED";
    public static final String PATH_INFO = "PATH_INFO";
    public static final String CONTENT_TYPE = "CONTENT_TYPE";
    public static final String CONTENT_LENGTH = "CONTENT_LENGTH";
    public static final String REMOTE_ADDR = "REMOTE_ADDR";
    public static final String REMOTE_PORT = "REMOTE_PORT";
    public static final String REMOTE_USER = "REMOTE_USER";
    public static final String HTTP_ACCEPT = "HTTP_ACCEPT";
    public static final String HTTP_COOKIE = "HTTP_COOKIE";
    public static final String HTTP_HOST = "HTTP_HOST";
    public static final String HTTP_USER_AGENT = "HTTP_USER_AGENT";
    public static final String HTTP_ACCEPT_ENCODING = "HTTP_ACCEPT_ENCODING";
    public static final String HTTP_ACCEPT_LANGUAGE = "HTTP_ACCEPT_LANGUAGE";
    public static final String HTTP_CONNECTION = "HTTP_CONNECTION";
    public static final String HTTP_UPGRADE_INSECURE_REQUESTS = "HTTP_UPGRADE_INSECURE_REQUESTS";
    public static final String HTTPS = "HTTPS";
    public static final String PATH = "PATH";
    public static final String SERVER_SIGNATURE = "SERVER_SIGNATURE";
    public static final String SERVER_SOFTWARE = "SERVER_SOFTWARE";
    public static final String SERVER_ADDR = "SERVER_ADDR";
    public static final String DOCUMENT_ROOT = "DOCUMENT_ROOT";
    public static final String REQUEST_SCHEME = "REQUEST_SCHEME";
    public static final String CONTEXT_PREFIX = "CONTEXT_PREFIX";
    public static final String CONTEXT_DOCUMENT_ROOT = "CONTEXT_DOCUMENT_ROOT";
    public static final String SERVER_ADMIN = "SERVER_ADMIN";
    public static final String REQUEST_TIME_FLOAT = "REQUEST_TIME_FLOAT";
    public static final String REQUEST_TIME = "REQUEST_TIME";
    public static final String UNIQUE_ID = "UNIQUE_ID";
    /*
    public static final String X_FORWARDED_HOST = "X_FORWARDED_HOST";
    public static final String X_FORWARDED_FOR = "X_FORWARDED_FOR";
    public static final String X_FORWARDED_PROTO = "X_FORWARDED_PROTO";
    public static final String X_FORWARDED_PORT = "X_FORWARDED_PORT";
    public static final String X_FORWARDED_SERVER = "X_FORWARDED_SERVER";
    */

    public static Map<String, String> getEnv(String path, String docRoot, String scriptBase, Tour tour) {

        Map<String, String> map = new HashMap<>();
        getEnv(path, docRoot, scriptBase, tour, (name, value) -> map.put(name, value));
        return map;
    }

    public static void getEnv(String path, String docRoot, String scriptBase, Tour tour, AddListener lis) {

        Headers reqHeaders = tour.req.headers;
        
        String ctype = reqHeaders.contentType();
        if(ctype != null) {
            int pos = ctype.indexOf("charset=");
            if(pos >= 0) {
                tour.req.setCharset(ctype.substring(pos+8).trim());
            }
        }

        addEnv(lis, REQUEST_METHOD, tour.req.method);
        addEnv(lis, REQUEST_URI, tour.req.uri);
        addEnv(lis, SERVER_PROTOCOL, tour.req.protocol);
        addEnv(lis, GATEWAY_INTERFACE, "CGI/1.1");

        addEnv(lis, SERVER_NAME, tour.req.reqHost);
        addEnv(lis, SERVER_ADDR, tour.req.serverAddress);
        if(tour.req.reqPort >= 0)
            addEnv(lis, SERVER_PORT, Integer.toString(tour.req.reqPort));
        addEnv(lis, SERVER_SOFTWARE, BayServer.getSoftwareName());

        addEnv(lis, CONTEXT_DOCUMENT_ROOT, docRoot);


        for(String name : tour.req.headers.headerNames()) {
            String newVal = null;
            for(String value : tour.req.headers.headerValues(name)) {
                if (newVal == null)
                    newVal = value;
                else {
                    newVal = newVal + "; " + value;
                }
            }

            name = name.toUpperCase().replace('-', '_');
            if(name.startsWith("X_FORWARDED_")) {
                addEnv(lis, name, newVal);
            }
            else {
                switch (name) {
                    case CONTENT_TYPE:
                    case CONTENT_LENGTH:
                        addEnv(lis, name, newVal);
                        break;

                    default:
                        addEnv(lis, "HTTP_" + name, newVal);
                        break;
                }
            }
        }

        addEnv(lis, REMOTE_ADDR, tour.req.remoteAddress);
        addEnv(lis, REMOTE_PORT, Integer.toString(tour.req.remotePort));
        //addEnv(map, REMOTE_USER, "unknown");

        addEnv(lis, REQUEST_SCHEME, tour.isSecure ? "https": "http");

        boolean tmpSecure = tour.isSecure;
        String fproto = tour.req.headers.get(Headers.X_FORWARDED_PROTO);
        if(fproto != null) {
            tmpSecure = fproto.equalsIgnoreCase("https");
        }
        if(tmpSecure)
            addEnv(lis, HTTPS, "on");

        addEnv(lis, QUERY_STRING, tour.req.queryString);
        addEnv(lis, SCRIPT_NAME, tour.req.scriptName);

        if(tour.req.pathInfo == null) {
            addEnv(lis, PATH_INFO, "");
        }
        else {
            addEnv(lis, PATH_INFO, tour.req.pathInfo);
            try {
                String pathTranslated = new File(docRoot, tour.req.pathInfo).getCanonicalPath();
                addEnv(lis, PATH_TRANSLATED, pathTranslated);
            }
            catch(IOException e) {
                BayLog.error(e);
            }
        }

        if(!scriptBase.endsWith("/"))
            scriptBase = scriptBase + "/";
        addEnv(lis, SCRIPT_FILENAME, scriptBase + tour.req.scriptName.substring(path.length()));
        addEnv(lis, PATH, System.getenv("PATH"));
    }
    
   
    private static void addEnv(AddListener lis, String key, Object value) {
        if(value == null)
            value = "";

        lis.add(key, value.toString());
    }
}