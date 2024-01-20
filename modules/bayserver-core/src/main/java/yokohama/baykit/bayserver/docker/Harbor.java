package yokohama.baykit.bayserver.docker;

import java.util.Locale;

public interface Harbor {

    enum FileSendMethod {
        Select,
        Spin,
        Taxi,
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

    /** Method to send file */
    FileSendMethod fileSendMethod();

    /** PID file name */
    String pidFile();

    /** Multi core flag */
    boolean multiCore();
}
