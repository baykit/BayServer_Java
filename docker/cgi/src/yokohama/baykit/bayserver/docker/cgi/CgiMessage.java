package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.util.Message;

import java.util.Locale;

public class CgiMessage extends Message {

    static CgiMessage msg;

    public static void init() throws ParseException {
        msg = new CgiMessage();
        msg.init(BayServer.bservHome + "/lib/conf/cgi_messages", Locale.getDefault());
    }

    public static String get(CgiSymbol key, Object... args) {
        return msg.getMessage(key, args);
    }
}
