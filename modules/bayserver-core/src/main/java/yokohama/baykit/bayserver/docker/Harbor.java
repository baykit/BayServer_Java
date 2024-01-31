package yokohama.baykit.bayserver.docker;

import java.util.Locale;

public interface Harbor {

    enum MultiPlexerType {
        Sensor,
        Spin,
        Pigeon,
        Job,
        Taxi,
        Train,
    };

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


    /** PID file name */
    String pidFile();

    /** Multi core flag */
    boolean multiCore();


    static String getMultiplexerTypeName(MultiPlexerType type) {
        switch (type) {
            case Sensor:
                return "sensor";
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
        switch (type) {
            case "sensor":
                return MultiPlexerType.Sensor;
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

}
