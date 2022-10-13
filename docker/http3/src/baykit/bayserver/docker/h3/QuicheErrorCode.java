package baykit.bayserver.docker.h3;

import baykit.bayserver.BayServer;
import baykit.bayserver.bcf.ParseException;
import baykit.bayserver.util.Message;

import java.util.Locale;

public class QuicheErrorCode extends Message {

    public static QuicheErrorCode msg;

    private QuicheErrorCode() {
    }

    public static String getMessage(int code) {
        return msg.getMessage(Integer.toString(code));
    }

    static {
        String prefix = BayServer.bservHome + "/lib/conf/quiche_messages";
        msg = new QuicheErrorCode();
        try {
            msg.init(prefix, Locale.getDefault());
        } catch (ParseException e) {
            throw new Error(e);
        }
    }

}
