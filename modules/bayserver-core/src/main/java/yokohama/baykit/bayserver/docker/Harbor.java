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

    /** Trouble docker */
    Trouble trouble();

    /** Socket timeout in seconds */
    int socketTimeoutSec();

    /** Keep-Alive timeout in seconds */
    int keepTimeoutSec();

    /** Trace req/res header flag */
    boolean traceHeader();

    /** Internal buffer size of Tour */
    int tourBufferSize();

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
