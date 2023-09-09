package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class StringUtil {
    public static List<String> falses = Arrays.asList("no", "false", "0", "off");
    public static List<String> trues = Arrays.asList("yes", "true", "1", "on");

    public static boolean isSet(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean empty(String str) {
        return !isSet(str);
    }

    /**
     * Maybe effective
     * @param str
     * @return
     */
    public static String toLowerCase(String str) {
        for(int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if(c >= 'A' && c <= 'Z') {
                return str.toLowerCase();
            }
        }
        return str;
    }

    public static byte[] toBytes(String str) {
        return str.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static String repeat(String str, int times) {
        String res = "";
        return IntStream.range(0, times).mapToObj((i) -> str).reduce(res, (s1, s2) -> s1 + s2);
    }

    public static String indent(int count) {
        return repeat(" ", count);
    }

    public static boolean parseBool(String val) {
        val = val.toLowerCase();
        if(trues.contains(val))
            return true;
        else if(falses.contains(val))
            return false;
        else {
            BayLog.warn("Invalid boolean value: " + val);
            return false;
        }
    }

    public static int parseSize(String value) throws NumberFormatException{
        value = value.toLowerCase();
        int rate = 1;
        if(value.endsWith("b"))
            value = value.substring(0, value.length() - 1);
        if(value.endsWith("k")) {
            value = value.substring(0, value.length() - 1);
            rate = 1024;
        }
        else if(value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
            rate = 1024 * 1024;
        }

        return Integer.parseInt(value) * rate;
    }

    public static String parseCharset(String charset) {
        try {
            "".getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            BayLog.error(e);
            return null;
        }
        return charset;
    }
}