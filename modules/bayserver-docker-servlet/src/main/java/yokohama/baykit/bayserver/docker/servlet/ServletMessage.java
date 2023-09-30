package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.util.Message;

import java.util.Locale;

public class ServletMessage extends Message {

    static ServletMessage msg;

    public static void init() throws ParseException {
        msg = new ServletMessage();
        msg.init("/conf/servlet_messages", Locale.getDefault());
    }

    public static String get(ServletSymbol key, Object... args) {
        return msg.getMessage(key, args);
    }
}
