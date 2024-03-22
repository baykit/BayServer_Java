package yokohama.baykit.bayserver.docker.http.h2;

public enum H2Type {

    Preface(-1),
    Data(0),
    Headers(1),
    Priority(2),
    RstStream(3),
    Settings(4),
    PushPromise(5),
    Ping(6),
    Goaway(7),
    WindowUpdate(8),
    Continuation(9);

    static H2Type[] types = {
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

    public int no;
    H2Type(int no) {
        this.no = no;
    }

    public static H2Type getType(int no) {
        if(no < 0 || no > types.length)
            return null;
        return types[no];
    }
}
