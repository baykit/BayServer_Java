package baykit.bayserver.docker.ajp;

/**
 * AJP command type
  */
public enum AjpType {

    Data(0),
    ForwardRequest(2),
    SendBodyChunk(3),
    SendHeaders(4),
    EndResponse(5),
    GetBodyChunk(6),
    Shutdown(7),
    Ping(8),
    CPing(10);

    static AjpType[] types = {
            Data,
            null,
            ForwardRequest,
            SendBodyChunk,
            SendHeaders,
            EndResponse,
            GetBodyChunk,
            Shutdown,
            Ping,
            null,
            CPing,
    };

    public int no;
    AjpType(int no) {
        this.no = no;
    }


    public static AjpType getType(int no) {
        if(no <= 0 || no >= types.length || types[no] == null)
            throw new IllegalArgumentException("Invalid AJP type: " + no);
        return types[no];
    }
}
