package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayServer;
import baykit.bayserver.bcf.*;
import yokohama.baykit.bayserver.util.Message;
import yokohama.baykit.bayserver.bcf.ParseException;

import java.util.HashMap;
import java.util.Locale;

public class H2ErrorCode extends Message {
    public static int NO_ERROR = 0x0;
    public static int PROTOCOL_ERROR = 0x1;
    public static int INTERNAL_ERROR = 0x2;
    public static int FLOW_CONTROL_ERROR = 0x3;
    public static int SETTINGS_TIMEOUT = 0x4;
    public static int STREAM_CLOSED = 0x5;
    public static int FRAME_SIZE_ERROR = 0x6;
    public static int REFUSED_STREAM = 0x7;
    public static int CANCEL = 0x8;
    public static int COMPRESSION_ERROR = 0x9;
    public static int CONNECT_ERROR = 0xa;
    public static int ENHANCE_YOUR_CALM = 0xb;
    public static int INADEQUATE_SECURITY = 0xc;
    public static int HTTP_1_1_REQUIRED = 0xd;

    static HashMap<Integer, String> desc = new HashMap<>();
    public static Message msg;

    private H2ErrorCode() {
    }

    public static void init() throws ParseException {
        if(msg != null)
            return;

        String prefix = BayServer.bservHome + "/lib/conf/h2_messages";
        msg = new H2ErrorCode();
        msg.init(prefix, Locale.getDefault());
    }
}
