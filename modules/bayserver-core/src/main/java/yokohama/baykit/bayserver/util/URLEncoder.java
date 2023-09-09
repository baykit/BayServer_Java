package yokohama.baykit.bayserver.util;

/**
 * Ad hoc encoder
 */
public class URLEncoder {

    /**
     * Encode tilde char only
     * 
     * @param url
     *            path
     * @return encoded string
     */
    public static String encodeTilde(String url) {

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '~')
                sb.append("%7E");
            else
                sb.append(c);
        }

        return sb.toString();
    }
}