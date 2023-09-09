package yokohama.baykit.bayserver.docker.fcgi;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 */
 public enum FcgType {

    BeginRequest(1),
    AbortRequest(2),
    EndRequest(3),
    Params(4),
    Stdin(5),
    Stdout(6),
    Stderr(7),
    Data(8),
    Getvalues(9),
    GetvaluesResult(10),
    UnkonwnType(11);

    static FcgType[] types = {
            BeginRequest,
            AbortRequest,
            EndRequest,
            Params,
            Stdin,
            Stdout,
            Stderr,
            Data,
            Getvalues,
            GetvaluesResult,
            UnkonwnType
    };

    public int no;
    FcgType(int no) {
        this.no = no;
    }

    public static FcgType getType(int no) {
        if(no <= 0 || no > types.length)
            throw new ArrayIndexOutOfBoundsException("Invalid FCGI type: " + no);
        return types[no - 1];
    }
}
