package baykit.bayserver;

/**
 * The constants
 * 
 */
public final class Constants {

    /** Line separator */
    public static String CRLF = "\r\n";
    public static byte[] CRLF_BYTES = CRLF.getBytes();

    /** Bytes of Space */
    public static String SPACE = " ";
    public static byte[] SPACE_BYTES = SPACE.getBytes();

    /** HTTP 1.1 Protocol header bytes */
    public static String HTTP_11 = "HTTP/1.1";
    public static byte[] HTTP_11_BYTES = HTTP_11.getBytes();

    /** HTTP 1.0 Protocol header bytes */
    public static String HTTP_10 = "HTTP/1.0";
    public static byte[] HTTP_10_BYTES = HTTP_10.getBytes();


    /** Default Consume request */
    public static final boolean DEFAULT_CONSUME_REQUEST = false;
    
    /** Default dump on exit */
    public static final boolean DEFAULT_DUMP_ON_EXIT = false;

    /**
     * default max header length
     */
    public static final int DEFAULT_MAX_HEADER_LENGTH = 1024;

    /**
     * default max header count
     */
    public static final int DEFAULT_MAX_HEADER_COUNT = 9999;

    /**
     * default session time out (300 seconds)
     */
    public static final int DEFAULT_SESSION_TIMEOUT = 300;

    /**
     * default max content length (1 kbytes)
     */
    public static final int DEFAULT_MAX_CONTENT_LENGTH = -1;

    /**
     * Default socket timeout
     */
    public static final int DEFAULT_SOCKET_TIMEOUT = 10;


    /**
     * Defualt decode tilde
     */
    public static final boolean DEFAULT_DECODE_TILDE = false;
    
    /**
     * Application user management type
     */
    public static final String USER_MANAGEMENT_TYPE_FILE = "file";

    /**
     * Default application user management type
     */
    public static final String DEFAULT_USER_MANAGEMENT_TYPE = 
        USER_MANAGEMENT_TYPE_FILE;


    /**
     * Content type url encoded
     */
    //public static final String CONTENT_TYPE_URL_ENCODED = "application/x-www-form-urlencoded";
    
}