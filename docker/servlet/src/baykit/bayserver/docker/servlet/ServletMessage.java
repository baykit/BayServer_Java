package baykit.bayserver.docker.servlet;

import baykit.bayserver.BayServer;
import baykit.bayserver.bcf.ParseException;
import baykit.bayserver.util.Message;

import java.util.Locale;

public class ServletMessage extends Message {

    static ServletMessage msg;

    public static void init() throws ParseException {
        msg = new ServletMessage();
        msg.init(BayServer.bservHome + "/lib/conf/servlet_messages", Locale.getDefault());
    }

    public static String get(ServletSymbol key, Object... args) {
        return msg.getMessage(key, args);
    }
}
