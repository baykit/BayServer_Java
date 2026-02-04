package yokohama.baykit.bayserver.docker.fcgi;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 */
 public class FcgType {

    public static final int BeginRequest = 1;
    public static final int AbortRequest = 2;
    public static final int EndRequest = 3;
    public static final int Params = 4;
    public static final int Stdin = 5;
    public static final int Stdout = 6;
    public static final int Stderr = 7;
    public static final int Data = 8;
    public static final int Getvalues = 9;
    public static final int GetvaluesResult = 10;
    public static final int UnkonwnType = 11;
 }
