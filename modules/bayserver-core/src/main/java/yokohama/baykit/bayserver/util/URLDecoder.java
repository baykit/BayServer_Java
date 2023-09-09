package yokohama.baykit.bayserver.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;

public class URLDecoder {

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

    /* Parse special character */
    static String parseSpecial(String str, String enc)
            throws UnsupportedEncodingException {

        ByteArrayOutputStream2 os = new ByteArrayOutputStream2();
        int index = 0;

        while (index < str.length()) {
            char c = str.charAt(index);

            switch (c) {
            case '+':
                os.write((byte) ' ');
                index++;
                break;

            case '%':
                String hexStr = str.substring(index + 1, index + 3);
                int ch = Integer.parseInt(hexStr, 16);
                os.write(ch);
                index += 3;
                break;

            default:
                os.write((byte) c);
                index++;
                break;
            }
        }

        byte[] b = os.getBuf();

        if (enc == null || enc.equals(""))
            return new String(b, 0, os.size());
        else
            return new String(b, 0, os.size(), enc);
    }

    private static class ByteArrayOutputStream2 extends ByteArrayOutputStream {

        byte[] getBuf() {
            return buf;
        }
    }
}