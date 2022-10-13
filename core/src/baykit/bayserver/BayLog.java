package baykit.bayserver;

import java.util.Date;
import java.util.IllegalFormatException;

public class BayLog {
    public static final int LOG_LEVEL_TRACE = 0;
    public static final int LOG_LEVEL_DEBUG = 1;
    public static final int LOG_LEVEL_INFO = 2;
    /** Log level */
    public static int logLevel = LOG_LEVEL_INFO;
    public static final int LOG_LEVEL_WARN = 3;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_FATAL = 5;
    public static final String[] LOG_LEVEL_NAME = {"TRACE", "DEBUG", "INFO ", "WARN ", "ERROR", "FATAL"};

    public static void setLogLevel(String s) {
        if(s.equalsIgnoreCase("trace"))
            logLevel = LOG_LEVEL_TRACE;
        else if(s.equalsIgnoreCase("debug"))
            logLevel = LOG_LEVEL_DEBUG;
        else if(s.equalsIgnoreCase("info"))
            logLevel = LOG_LEVEL_INFO;
        else if(s.equalsIgnoreCase("warn"))
            logLevel = LOG_LEVEL_WARN;
        else if(s.equalsIgnoreCase("error"))
            logLevel = LOG_LEVEL_ERROR;
        else if(s.equalsIgnoreCase("fatal"))
            logLevel = LOG_LEVEL_FATAL;
        else
            warn(BayMessage.get(Symbol.INT_UNKNOWN_LOG_LEVEL, s));
    }

    ////////////////////////////////////////////////////////////////
    // logging
    ////////////////////////////////////////////////////////////////
    public static void log(int lvl, String fmt, Object... args) {
        log(lvl, 3, fmt, args);
    }

    public static void info(String fmt, Object... args) {
        log(LOG_LEVEL_INFO, 3, fmt, args);
    }

    public static void trace(String fmt, Object... args) {
        log(LOG_LEVEL_TRACE, 3, fmt, args);
    }

    public static void debug(String fmt, Object... args) {
        log(LOG_LEVEL_DEBUG, 3, fmt, args);
    }

    public static void debug(Throwable e, String fmt, Object... args) {
        log(LOG_LEVEL_DEBUG, 4, e, fmt, args);
    }

    public static void warn(String fmt, Object... args) {
        log(LOG_LEVEL_WARN, 3, fmt, args);
    }

    public static void warn(Throwable e, String fmt, Object... args) {
        log(LOG_LEVEL_WARN, 4, e, fmt, args);
    }

    public static void error(Throwable e) {
        log(LOG_LEVEL_ERROR, 4, e, null);
    }

    public static void error(Throwable e, String fmt, Object... args) {
        log(LOG_LEVEL_ERROR, 4, e, fmt, args);
    }

    public static void error(String fmt, Object... args) {
        log(LOG_LEVEL_ERROR, 4, null, fmt, args);
    }

    public static void fatal(Throwable e) {
        fatal(4, e, null);
    }

    public static void fatal(String fmt, Object... args) {
        log(LOG_LEVEL_FATAL, 4, null, fmt, args);
    }

    public static void fatal(Throwable e, String fmt, Object... args) {
        fatal(4, e, fmt, args);
    }

    public static void log(int lvl, int stackIdx, String fmt, Object... args) {
        if(lvl >= logLevel) {
            synchronized (BayServer.class) {
                StackTraceElement caller = Thread.currentThread().getStackTrace()[stackIdx];
                System.err.print("[" + new Date() + "] ");
                System.err.print(LOG_LEVEL_NAME[lvl]);
                System.err.print(". ");
                String msg = "";
                Exception err = null;
                try {
                    if(args == null || args.length == 0)
                        msg = String.format("%s", fmt);
                    else
                        msg = String.format(fmt, args);
                }
                catch(IllegalFormatException e) {
                    e.printStackTrace();
                    msg = fmt;
                }
                System.err.print(msg);
                System.err.print(" (");
                System.err.print(caller.getFileName());
                System.err.print(":");
                System.err.print(caller.getLineNumber());
                System.err.println(")");

                if(err != null)
                    error(err);
            }
        }
    }

    public static void log(int lvl, int stackIdx, Throwable e, String fmt, Object... args) {
        if(fmt != null) {
            log(lvl, stackIdx, fmt, args);
        }
        if(e != null) {
            if(isDebugMode()) {
                e.printStackTrace(System.err);
            }
            else {
                log(lvl, stackIdx, "%s", e.toString());
            }
        }
    }

    private static void fatal(int stackIdx, Throwable e, String fmt, Object... args) {
        if(fmt != null)
            log(LOG_LEVEL_FATAL, stackIdx, fmt, args);
        if(e != null)
            e.printStackTrace();
        System.exit(1);
    }

    public static boolean isDebugMode() {
        return logLevel <= LOG_LEVEL_DEBUG;
    }

    public static boolean isTraceMode() {
        return logLevel == LOG_LEVEL_TRACE;
    }
}
