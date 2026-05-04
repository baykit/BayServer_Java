package yokohama.baykit.bayserver;

import yokohama.baykit.bayserver.util.RoughTime;

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

    // Fast-path overloads: gate on logLevel BEFORE the varargs Object[] is
    // allocated and BEFORE any int->Integer autoboxing kicks in. The
    // varargs version (Object...) stays as the fallback for >4 args; the
    // 0-4-arg versions cover the overwhelming majority of call sites.
    // Hot-path proxy bench showed BayLog.debug at 9% of CPU samples even
    // with logLevel=INFO because the varargs array allocation happened
    // before the suppression check.

    public static void trace(String fmt) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, (Object[]) null);
    }
    public static void trace(String fmt, Object a) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{a});
    }
    public static void trace(String fmt, Object a, Object b) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{a, b});
    }
    public static void trace(String fmt, Object a, Object b, Object c) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{a, b, c});
    }
    public static void trace(String fmt, Object a, Object b, Object c, Object d) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{a, b, c, d});
    }
    public static void trace(String fmt, Object... args) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, args);
    }

    public static void debug(String fmt) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, (Object[]) null);
    }
    public static void debug(String fmt, Object a) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a});
    }
    public static void debug(String fmt, Object a, Object b) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a, b});
    }
    public static void debug(String fmt, Object a, Object b, Object c) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a, b, c});
    }
    public static void debug(String fmt, Object a, Object b, Object c, Object d) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a, b, c, d});
    }
    public static void debug(String fmt, Object a, Object b, Object c, Object d, Object e) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a, b, c, d, e});
    }
    public static void debug(String fmt, Object a, Object b, Object c, Object d, Object e, Object f) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{a, b, c, d, e, f});
    }
    public static void debug(String fmt, Object... args) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, args);
    }

    public static void debug(Throwable e, String fmt, Object... args) {
        log(LOG_LEVEL_DEBUG, 4, e, fmt, args);
    }

    public static void debug(Throwable e) {
        log(LOG_LEVEL_DEBUG, 4, e, null);
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
        log(LOG_LEVEL_FATAL, 4, e, null);
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
                System.err.print("[" + RoughTime.currentDate() + "] ");
                System.err.print(LOG_LEVEL_NAME[lvl]);
                System.err.print(". ");
                String msg = "";
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
            }
        }
    }

    public static void log(int lvl, int stackIdx, Throwable e, String fmt, Object... args) {
        if(fmt != null) {
            log(lvl, stackIdx, fmt, args);
        }
        if(e != null) {
            if(isDebugMode()) {
                log(lvl, stackIdx, "%s", "Exception!");
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
    }

    public static boolean isDebugMode() {
        return logLevel <= LOG_LEVEL_DEBUG;
    }

    public static boolean isTraceMode() {
        return logLevel == LOG_LEVEL_TRACE;
    }
}
