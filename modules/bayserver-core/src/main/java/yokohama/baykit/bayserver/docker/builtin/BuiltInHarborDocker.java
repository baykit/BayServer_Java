package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Harbor;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Trouble;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import yokohama.baykit.bayserver.common.Groups;
import yokohama.baykit.bayserver.util.LocaleUtil;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.FileNotFoundException;
import java.util.Locale;

public class BuiltInHarborDocker extends DockerBase implements Harbor {

    public static final int DEFAULT_MAX_SHIPS = 256;
    public static final int DEFAULT_GRAND_AGENTS = 0;
    public static final int DEFAULT_TRAIN_RUNNERS = 8;
    public static final int DEFAULT_TAXI_RUNNERS = 8;
    public static final int DEFAULT_SOCKET_TIMEOUT_SEC = 300;
    public static final int DEFAULT_KEEP_TIMEOUT_SEC = 20;
    public static final int DEFAULT_TOUR_BUFFER_SIZE = 1024 * 1024;  // 1M
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final int DEFAULT_CONTROL_PORT = -1;
    public static final MultiPlexerType DEFAULT_NET_MULTIPLEXER = MultiPlexerType.Sensor;
    public static final MultiPlexerType DEFAULT_FILE_MULTIPLEXER = MultiPlexerType.Taxi;
    public static final MultiPlexerType DEFAULT_LOG_MULTIPLEXER = MultiPlexerType.Taxi;
    public static final MultiPlexerType DEFAULT_CGI_MULTIPLEXER = MultiPlexerType.Taxi;
    public static final RecipientType DEFAULT_RECIPIENT = RecipientType.Spider;
    public static final boolean DEFAULT_MULTI_CORE = true;
    public static final boolean DEFAULT_GZIP_COMP = false;
    public static final String DEFAULT_PID_FILE = "bayserver.pid";

    /** Default charset */
    String charset = DEFAULT_CHARSET;

    /** Default locale */
    Locale locale;

    /** Number of grand agents */
    int grandAgents = DEFAULT_GRAND_AGENTS;

    /** Number of train runners */
    int trainRunners = DEFAULT_TRAIN_RUNNERS;

    /** Number of taxi runners */
    int taxiRunners = DEFAULT_TAXI_RUNNERS;

    /** Max count of ships */
    int maxShips = DEFAULT_MAX_SHIPS;

    /** Socket timeout in seconds */
    int socketTimeoutSec = DEFAULT_SOCKET_TIMEOUT_SEC;

    /** Keep-Alive timeout in seconds */
    int keepTimeoutSec = DEFAULT_KEEP_TIMEOUT_SEC;

    /** Internal buffer size of Tour */
    int tourBufferSize = DEFAULT_TOUR_BUFFER_SIZE;

    /** Trace req/res header flag */
    boolean traceHeader = false;

    /** Trouble docker */
    Trouble trouble;

    /** File name to redirect stdout/stderr */
    String redirectFile;

    /** Gzip compression flag */
    boolean gzipComp = DEFAULT_GZIP_COMP;

    /** Port number of signal agent */
    int controlPort = DEFAULT_CONTROL_PORT;

    /** Multi core flag */
    boolean multiCore = DEFAULT_MULTI_CORE;

    /** Multiplexer type of network I/O */
    MultiPlexerType netMultiplexer = DEFAULT_NET_MULTIPLEXER;

    /** Multiplexer type of file read */
    MultiPlexerType fileMultiplexer = DEFAULT_FILE_MULTIPLEXER;

    /** Multiplexer type of log output */
    Harbor.MultiPlexerType logMultiplexer = DEFAULT_LOG_MULTIPLEXER;

    /** Multiplexer type of CGI input */
    Harbor.MultiPlexerType cgiMultiplexer = DEFAULT_CGI_MULTIPLEXER;

    /** Recipient type */
    RecipientType recipient = DEFAULT_RECIPIENT;

    /** PID file name */
    String pidFile = DEFAULT_PID_FILE;

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if (grandAgents <= 0)
            grandAgents = Runtime.getRuntime().availableProcessors();
        if (trainRunners <= 0)
            trainRunners = 1;
        if (maxShips <= 0)
            maxShips = DEFAULT_MAX_SHIPS;

        if (maxShips < DEFAULT_MAX_SHIPS) {
            maxShips = DEFAULT_MAX_SHIPS;
            BayLog.warn(BayMessage.get(Symbol.CFG_MAX_SHIPS_IS_TO_SMALL, maxShips));
        }

        if (!multiCore) {
            BayLog.warn(BayMessage.get(Symbol.CFG_SINGLE_CORE_NOT_SUPPORTED));
            multiCore = true;
        }

        if (netMultiplexer == MultiPlexerType.Taxi ||
                netMultiplexer == MultiPlexerType.Train ||
                netMultiplexer == MultiPlexerType.Spin) {
            BayLog.warn(
                    BayMessage.get(
                            Symbol.CFG_NET_MULTIPLEXER_NOT_SUPPORTED,
                            Harbor.getMultiplexerTypeName(netMultiplexer),
                            Harbor.getMultiplexerTypeName(DEFAULT_NET_MULTIPLEXER)));
            netMultiplexer = DEFAULT_NET_MULTIPLEXER;
        }

        if ((fileMultiplexer == MultiPlexerType.Sensor && !SysUtil.supportSelectFile()) ||
                (fileMultiplexer == MultiPlexerType.Spin && !SysUtil.supportNonblockFileRead()) ||
                (fileMultiplexer == MultiPlexerType.Train)) {
            BayLog.warn(
                    BayMessage.get(
                            Symbol.CFG_FILE_MULTIPLEXER_NOT_SUPPORTED,
                            Harbor.getMultiplexerTypeName(fileMultiplexer),
                            Harbor.getMultiplexerTypeName(DEFAULT_FILE_MULTIPLEXER)));
            fileMultiplexer = DEFAULT_FILE_MULTIPLEXER;
        }

        if((logMultiplexer == Harbor.MultiPlexerType.Sensor && !SysUtil.supportSelectFile()) ||
                (logMultiplexer == Harbor.MultiPlexerType.Train)) {
            BayLog.warn(
                    BayMessage.get(
                            Symbol.CFG_LOG_MULTIPLEXER_NOT_SUPPORTED,
                            Harbor.getMultiplexerTypeName(logMultiplexer),
                            Harbor.getMultiplexerTypeName(DEFAULT_LOG_MULTIPLEXER)));
            logMultiplexer = DEFAULT_LOG_MULTIPLEXER;
        }

        if (cgiMultiplexer == Harbor.MultiPlexerType.Sensor ||
                cgiMultiplexer == Harbor.MultiPlexerType.Spin ||
                cgiMultiplexer == Harbor.MultiPlexerType.Pigeon) {
            BayLog.warn(ConfigException.createMessage(
                    BayMessage.get(
                            Symbol.CFG_CGI_MULTIPLEXER_NOT_SUPPORTED,
                            Harbor.getMultiplexerTypeName(cgiMultiplexer),
                            Harbor.getMultiplexerTypeName(DEFAULT_CGI_MULTIPLEXER)),
                    elm.fileName,
                    elm.lineNo));
            cgiMultiplexer = DEFAULT_CGI_MULTIPLEXER;
        }

        if (netMultiplexer == MultiPlexerType.Sensor &&
            recipient != RecipientType.Spider) {
            BayLog.warn(ConfigException.createMessage(
                    BayMessage.get(
                            Symbol.CFG_NET_MULTIPLEXER_DOES_NOT_SUPPORT_THIS_RECIPIENT,
                            Harbor.getMultiplexerTypeName(netMultiplexer),
                            Harbor.getRecipientTypeName(recipient),
                            Harbor.getRecipientTypeName(RecipientType.Spider)),
                    elm.fileName,
                    elm.lineNo));
            recipient = RecipientType.Spider;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////

    @Override
    public boolean initDocker(Docker dkr) throws ConfigException {
        if (dkr instanceof Trouble) {
            trouble = (Trouble) dkr;
            return true;
        }
        else
            return super.initDocker(dkr);
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return false;

            case "loglevel":
                BayLog.setLogLevel(kv.value);
                break;

            case "charset":
                String charset = StringUtil.parseCharset(kv.value);
                if(StringUtil.isSet(charset))
                    this.charset = charset;
                break;

            case "locale":
                this.locale = LocaleUtil.parseLocale(kv.value);
                break;

            case "groups": {
                try {
                    String fname = BayServer.parsePath(kv.value);
                    Groups.init(fname);
                }
                catch(FileNotFoundException e) {
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.get(Symbol.CFG_FILE_NOT_FOUND, kv.value));
                }
                break;
            }

            case "grandagents":
                this.grandAgents = Integer.parseInt(kv.value);
                break;

            case "trains":
                this.trainRunners = Integer.parseInt(kv.value);
                break;

            case "taxis":
            case "taxies":
                this.taxiRunners = Integer.parseInt(kv.value);
                break;

            case "maxships":
                this.maxShips = Integer.parseInt(kv.value);
                break;

            case "timeout":
                this.socketTimeoutSec = Integer.parseInt(kv.value);
                break;

            case "keeptimeout":
                this.keepTimeoutSec = Integer.parseInt(kv.value);
                break;

            case "tourbuffersize":
                this.tourBufferSize = StringUtil.parseSize(kv.value);
                break;

            case "traceheader":
                traceHeader = StringUtil.parseBool(kv.value);
                break;

            case "redirectfile":
                redirectFile = kv.value;
                break;

            case "controlport":
                controlPort = Integer.parseInt(kv.value);
                break;

            case "multicore":
                multiCore = StringUtil.parseBool(kv.value);
                break;

            case "gzipcomp":
                gzipComp = StringUtil.parseBool(kv.value);
                break;

            case "netmultiplexer":
                try {
                    netMultiplexer = Harbor.getMultiplexerType(kv.value.toLowerCase());
                }
                catch(IllegalArgumentException e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
                break;

            case "filemultiplexer":
                try {
                    fileMultiplexer = Harbor.getMultiplexerType(kv.value.toLowerCase());
                }
                catch(IllegalArgumentException e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }

                break;

            case "logmultiplexer":
                try {
                    logMultiplexer = Harbor.getMultiplexerType(kv.value.toLowerCase());
                }
                catch(IllegalArgumentException e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
                break;

            case "cgimultiplexer": {
                try {
                    cgiMultiplexer = Harbor.getMultiplexerType(kv.value.toLowerCase());
                }
                catch (IllegalArgumentException e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
                break;
            }

            case "recipient": {
                try {
                    recipient = Harbor.getRecipientType(kv.value.toLowerCase());
                }
                catch (IllegalArgumentException e) {
                    BayLog.error(e);
                    throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
                break;
            }

            case "pidfile":
                pidFile = kv.value;
                break;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Override methods in Harbor
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String charset() {
        return charset;
    }

    @Override
    public Locale locale() {
        return locale;
    }

    @Override
    public int grandAgents() {
        return grandAgents;
    }

    @Override
    public int trainRunners() {
        return trainRunners;
    }

    @Override
    public int taxiRunners() {
        return taxiRunners;
    }

    @Override
    public int maxShips() {
        return maxShips;
    }

    @Override
    public Trouble trouble() {
        return trouble;
    }


    @Override
    public int socketTimeoutSec() {
        return socketTimeoutSec;
    }

    @Override
    public int keepTimeoutSec() {
        return keepTimeoutSec;
    }

    @Override
    public boolean traceHeader() {
        return traceHeader;
    }

    @Override
    public int tourBufferSize() {
        return tourBufferSize;
    }

    @Override
    public String redirectFile() {
        return redirectFile;
    }

    @Override
    public int controlPort() {
        return controlPort;
    }

    @Override
    public boolean gzipComp() {
        return gzipComp;
    }

    @Override
    public MultiPlexerType netMultiplexer() {
        return netMultiplexer;
    }

    @Override
    public MultiPlexerType fileMultiplexer() {
        return fileMultiplexer;
    }

    @Override
    public MultiPlexerType logMultiplexer() {
        return logMultiplexer;
    }

    @Override
    public MultiPlexerType cgiMultiplexer() {
        return cgiMultiplexer;
    }

    @Override
    public RecipientType recipient() {
        return recipient;
    }

    @Override
    public String pidFile() {
        return pidFile;
    }

    @Override
    public boolean multiCore() {
        return multiCore;
    }
}
