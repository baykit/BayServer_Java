package yokohama.baykit.bayserver.docker.http.h2;

public class H2Type {

    public static final int Preface = -1;
    public static final int Data = 0;
    public static final int Headers = 1;
    public static final int Priority = 2;
    public static final int RstStream = 3;
    public static final int Settings = 4;
    public static final int PushPromise = 5;
    public static final int Ping = 6;
    public static final int Goaway = 7;
    public static final int WindowUpdate = 8;
    public static final int Continuation = 9;

    static int[] types = {
            Data,
            Headers,
            Priority,
            RstStream,
            Settings,
            PushPromise,
            Ping,
            Goaway,
            WindowUpdate,
            Continuation
    };
}
