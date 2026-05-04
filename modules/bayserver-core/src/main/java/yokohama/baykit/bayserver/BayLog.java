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

    /**
     * Fixed-arity overload (0..10 Object args).
     *
     * Why fixed-arity instead of just the {@code (String, Object...)} version:
     * a varargs call allocates a fresh {@code Object[]} on every invocation,
     * BEFORE the called method has any chance to inspect logLevel and bail
     * out. Hot-path proxy benches showed {@link #debug} at 9% of CPU samples
     * with logLevel=INFO because the array allocation (and any int->Integer
     * autoboxing on the args) ran on every suppressed call.
     *
     * The fixed-arity overloads gate on logLevel FIRST so a suppressed call
     * site pays only the level comparison and a return. The varargs version
     * is kept as a fallback for >10 args. Coverage up to 10 args matches the
     * widest call sites we have today.
     */
    public static void trace(String fmt) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, (Object[]) null);
    }
    public static void trace(String fmt, Object arg1) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1});
    }
    public static void trace(String fmt, Object arg1, Object arg2) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9});
    }
    public static void trace(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10});
    }
    public static void trace(String fmt, Object... args) {
        if (logLevel > LOG_LEVEL_TRACE) return;
        log(LOG_LEVEL_TRACE, 3, fmt, args);
    }

    public static void debug(String fmt) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, (Object[]) null);
    }
    public static void debug(String fmt, Object arg1) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1});
    }
    public static void debug(String fmt, Object arg1, Object arg2) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9});
    }
    public static void debug(String fmt, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        if (logLevel > LOG_LEVEL_DEBUG) return;
        log(LOG_LEVEL_DEBUG, 3, fmt, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10});
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
