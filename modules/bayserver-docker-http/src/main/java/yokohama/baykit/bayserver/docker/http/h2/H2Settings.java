package yokohama.baykit.bayserver.docker.http.h2;

public class H2Settings {
    
    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public static final boolean DEFAULT_ENABLE_PUSH = true;
    public static final int DEFAULT_MAX_CONCURRENT_STREAMS = -1;
    public static final int DEFAULT_MAX_WINDOW_SIZE = 65535;
    public static final int DEFAULT_MAX_FRAME_SIZE = 16384;
    public static final int DEFAULT_MAX_HEADER_LIST_SIZE = -1;


    public int headerTableSize;
    public boolean enablePush;
    public int maxConcurrentStreams;
    public int initialWindowSize;
    public int maxFrameSize;
    public int maxHeaderListSize;
    
    public H2Settings() {
        reset();
    }
    
    void reset() {
        headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
        enablePush = DEFAULT_ENABLE_PUSH;
        maxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
        initialWindowSize = DEFAULT_MAX_WINDOW_SIZE;
        maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
        maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
    }
}
