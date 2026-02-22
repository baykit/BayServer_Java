package yokohama.baykit.bayserver.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;

public class URLDecoder {

    // Supports only ASCII '0'..'9', 'A'..'F', 'a'..'f'. All others map to -1.
    private static final int[] HEX = new int[128];

    /**
     * Decode tilde char only
     * 
     * @param url
     *            path
     * @return decoded string
     */
    public static String decodeTilde(String url) {

        while(true) {
            int pos = url.indexOf("%7E");
            if(pos == -1)
                pos = url.indexOf("%7e");
            if(pos == -1)
                break;
            
            url = url.substring(0, pos) + "~" + url.substring(pos + 3);
        }
        
        return url;
    }    
    
    
    /**
     * v: undecoded strings{[name1, value1], [name2, value2], ....} return:
     * decoded strings {[NAME1, VALUE1], [NAME2, VALUE2],... }
     */
    public static ArrayList decodeCGIParams(ArrayList list, String enc)
            throws UnsupportedEncodingException {

        ArrayList ret = new ArrayList();
        Iterator pairs = list.iterator();
        while (pairs.hasNext()) {
            String[] pair = (String[]) pairs.next();

            String name = parseSpecial(pair[0], enc);
            String value = parseSpecial(pair[1], enc);
            ret.add(new String[] { name, value });
        }
        return ret;
    }

    public static String decode(String str, String enc)
            throws UnsupportedEncodingException {
        return parseSpecial(str, enc);
    }


    static String parseSpecial(String s, String enc) throws UnsupportedEncodingException {
        final int n = s.length();
        // The output will never be longer than the input
        // (%xx sequences shrink from 3 characters to 1 byte)
        byte[] out = new byte[n];
        int o = 0;

        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (c == '+') {
                out[o++] = (byte) ' ';
                i++;
            }
            else if (c == '%') {
                if (i + 2 >= n)
                    throw new UnsupportedEncodingException("Bad percent-encoding");
                int hi = HEX[s.charAt(i + 1)];
                int lo = HEX[s.charAt(i + 2)];
                if ((hi | lo) < 0)
                    throw new UnsupportedEncodingException("Bad percent-encoding");
                out[o++] = (byte) ((hi << 4) | lo);
                i += 3;
            }
            else {
                // This is correct and fast only when the input is assumed to be ASCII-compatible
                out[o++] = (byte) c;
                i++;
            }
        }

        Charset cs;
        if (enc == null || enc.isEmpty())
            cs = StandardCharsets.UTF_8;
        else
            cs = Charset.forName(enc);

        return new String(out, 0, o, cs);
    }

    static {
        java.util.Arrays.fill(HEX, -1);
        for (int i = '0'; i <= '9'; i++)
            HEX[i] = i - '0';

        for (int i = 'A'; i <= 'F'; i++)
            HEX[i] = 10 + (i - 'A');

        for (int i = 'a'; i <= 'f'; i++)
            HEX[i] = 10 + (i - 'a');
    }
}
