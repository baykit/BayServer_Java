package yokohama.baykit.bayserver.docker;

import java.util.Locale;

public interface Harbor {

    enum MultiPlexerType {
        Spider,
        Spin,
        Pigeon,
        Job,
        Taxi,
        Train,
    }

    enum RecipientType {
        Spider,
        Pipe
    }

    /** Default charset */
    String charset();

    /** Default locale */
    Locale locale();

    /** Number of grand agents */
    int grandAgents();

    /** Number of train runners */
    int trainRunners();

    /** Number of taxi runners */
    int taxiRunners();

    /** Max count of ships */
    int maxShips();

    /** Max count of tours per ship (limits H2 concurrent streams per connection) */
    int maxToursPerShip();

    /** Trouble docker */
    Trouble trouble();

    /** Socket timeout in seconds */
    int socketTimeoutSec();

    /** Keep-Alive timeout in seconds */
    int keepTimeoutSec();

    /** Trace req/res header flag */
    boolean traceHeader();

    /** Internal buffer size of Tour */
    int shipBufferSize();

    /**
     * Maximum number of epoll events a single SpiderMultiplexer.receive()
     * call will hand off to handleChannel() before returning.  Any leftover
     * ready keys stay in the selected-key set and are drained on subsequent
     * receive() calls without re-entering select().  Keeps the agent's hot
     * working set inside per-core L2 cache under high -c workloads.
     *
     * -1 (the default) disables the limit entirely and restores the
     * pre-tuning behaviour of draining the whole selected-key set in one
     * pass.  A positive value (e.g. 32 on Xeon Skylake-class L2=1MB)
     * activates the cap.  Since the optimal value depends on per-core L2
     * size and per-connection hot footprint, we require operators to opt
     * in rather than hard-coding a machine-specific default.
     */
    int maxEventsPerReceive();

    /** File name to redirect stdout/stderr */
    String redirectFile();

    /** Port number of signal agent */
    int controlPort();

    /** Gzip compression flag */
    boolean gzipComp();

    /** Multiplexer of Network I/O */
    MultiPlexerType netMultiplexer();

    /** Multiplexer of File I/O */
    MultiPlexerType fileMultiplexer();

    /** Multiplexer of Log output */
    MultiPlexerType logMultiplexer();

    /** Multiplexer of CGI input */
    MultiPlexerType cgiMultiplexer();

    /** Recipient */
    RecipientType recipient();

    /** PID file name */
    String pidFile();

    /** Multi core flag */
    boolean multiCore();

    /**
     * Whether to enable Direct Boarding (the sendfile API).
     * This bypasses user-space formalities for efficient data transfer.
     */
    boolean directBoarding();

    /**
     * The lifespan, in seconds, of a cargo (cached file).
     */
    int cargoLifespanSec();

    /**
     * The maximum number of files (file descriptors) to be cached for Direct Boarding.
     * When this limit is reached, the least recently used (LRU) items are evicted.
     */
    int maxDirectBoardings();

    /**
     * The maximum file size, in bytes, to be cached.
     * Files exceeding this size will not be cached.
     */
    int maxCargoSize();

    /**
     * The maximum file size, in bytes, for Direct Boarding (sendfile/transferTo).
     * Files exceeding this size will not use the sendfile API.
     */
    int maxDirectBoardingSize();

    /**
     * Find barge by path
     */
    Barge findBarge(String path);


    static String getMultiplexerTypeName(MultiPlexerType type) {
        switch (type) {
            case Spider:
                return "spider";
            case Spin:
                return "spin";
            case Pigeon:
                return "pigeon";
            case Job:
                return "job";
            case Taxi:
                return "taxi";
            case Train:
                return "train";
            default:
                return null;
        }
    }

    static MultiPlexerType getMultiplexerType(String type) {
        if(type != null)
            type = type.toLowerCase();
        switch (type) {
            case "spider":
                return MultiPlexerType.Spider;
            case "spin":
                return MultiPlexerType.Spin;
            case "pigeon":
                return MultiPlexerType.Pigeon;
            case "job":
                return MultiPlexerType.Job;
            case "taxi":
                return MultiPlexerType.Taxi;
            case "train":
                return MultiPlexerType.Train;
            default:
                throw new IllegalArgumentException();
        }
    }

    static String getRecipientTypeName(RecipientType type) {
        switch (type) {
            case Spider:
                return "spider";

            case Pipe:
                return "pipe";

            default:
                return null;
        }
    }

    static RecipientType getRecipientType(String type) {
        if(type != null)
            type = type.toLowerCase();
        switch (type) {
            case "spider":
                return RecipientType.Spider;
            case "pipe":
                return RecipientType.Pipe;
            default:
                throw new IllegalArgumentException();
        }
    }
}
