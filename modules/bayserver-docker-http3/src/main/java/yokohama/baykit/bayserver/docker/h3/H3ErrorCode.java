package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.util.Message;

import java.util.Locale;

public class H3ErrorCode extends Message {

    public static H3ErrorCode msg;

    private H3ErrorCode() {
    }

    public static String getMessage(int code) {
        return msg.getMessage(Integer.toString(code));
    }

    static {
        String prefix = BayServer.bservHome + "/lib/conf/h3_messages";
        msg = new H3ErrorCode();
        try {
            msg.init(prefix, Locale.getDefault());
        } catch (ParseException e) {
            throw new Error(e);
        }
    }

}
