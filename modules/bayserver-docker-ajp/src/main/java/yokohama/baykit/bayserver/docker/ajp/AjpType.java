package yokohama.baykit.bayserver.docker.ajp;

/**
 * AJP command type
  */
public class AjpType {

    public static final int Data = 0;
    public static final int ForwardRequest = 2;
    public static final int SendBodyChunk = 3;
    public static final int SendHeaders = 4;
    public static final int EndResponse = 5;
    public static final int GetBodyChunk = 6;
    public static final int Shutdown = 7;
    public static final int Ping = 8;
    public static final int CPing = 10;
}
