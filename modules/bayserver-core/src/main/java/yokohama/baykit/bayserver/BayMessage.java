package yokohama.baykit.bayserver;

import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.util.Message;

import java.util.Locale;


public class BayMessage {

    static Message msg = new Message();

    public static void init(String conf, Locale locale) throws ParseException {
        msg.init(conf, locale);
    }

    public static String get(Symbol key, Object... args) {
        return msg.getMessage(key, args);
    }

    public static String CFG_INVALID_PARAMETER(String name) {
        return BayMessage.get(Symbol.CFG_INVALID_PARAMETER, name);
    }

    public static String CFG_INVALID_PARAMETER_VALUE(String value) {
        return BayMessage.get(Symbol.CFG_INVALID_PARAMETER_VALUE, value);
    }

    public static String CFG_INVALID_DOCKER(String name) {
        return BayMessage.get(Symbol.CFG_INVALID_DOCKER, name);
    }

    public static String CFG_INVALID_LOCATION(String location) {
        return BayMessage.get(Symbol.CFG_INVALID_LOCATION, location);
    }

    public static String CFG_INVALID_LOG_FORMAT(String format) {
        return BayMessage.get(Symbol.CFG_INVALID_LOG_FORMAT, format);
    }
    
    public static String CFG_GROUP_NOT_FOUND(String group){
        return BayMessage.get(Symbol.CFG_GROUP_NOT_FOUND, group);
    }

    public static String CFG_INVALID_PERMISSION_DESCRIPTION(String desc) {
        return BayMessage.get(Symbol.CFG_INVALID_PERMISSION_DESCRIPTION, desc);
    }

    public static String CFG_INVALID_WARP_DESTINATION(String destination) {
        return BayMessage.get(Symbol.CFG_INVALID_WARP_DESTINATION, destination);
    }

    public static String CFG_PARAMETER_IS_NOT_A_NUMBER(String name, String value) {
        return BayMessage.get(Symbol.CFG_PARAMETER_IS_NOT_A_NUMBER, name, value);
    }

}
