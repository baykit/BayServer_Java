package baykit.bayserver.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class KeyValListParser {

    /** Char to separate parameters */
    char separateParams = '&';

    /** Char to separete name and value */
    char separateNameValue = '=';

    public KeyValListParser(char itemSep, char keyValSep) {
        separateParams = itemSep;
        separateNameValue = keyValSep;
    }

    public KeyValListParser() {
    }

    public ArrayList<KeyVal> parse(String str) {
        ArrayList<KeyVal> list = new ArrayList();

        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < str.length(); i++) {

            char c = str.charAt(i);
            if (c == separateParams) {
                list.add(devideParam(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            list.add(devideParam(sb.toString()));
            sb.setLength(0);
        }

        return list;
    }

    public ArrayList<KeyVal> parse(InputStream stream) throws IOException {
        ArrayList<KeyVal> list = new ArrayList();

        StringBuffer sb = new StringBuffer();

        while (true) {

            int c = stream.read();
            if (c == -1)
                break;

            if (c == separateParams) {
                list.add(devideParam(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append((char) c);
            }
        }

        if (sb.length() > 0) {
            list.add(devideParam(sb.toString()));
            sb.setLength(0);
        }

        return list;
    }

    /**
     * name=value -> NV[name, value]
     * @param param
     * @return
     */
    private KeyVal devideParam(String param) {

        String name, value;

        int pos = param.indexOf(separateNameValue);
        if (pos == -1) {
            name = param;
            value = "";
        } else {
            name = param.substring(0, pos);
            value = param.substring(pos + 1);
        }

        name = name.trim();
        return new KeyVal(name, value);
    }
}