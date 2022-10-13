package baykit.bayserver.util;

import java.util.Locale;
import java.util.StringTokenizer;

public class LocaleUtil {
    public static Locale parseLocale(String locale) {
        StringTokenizer st = new StringTokenizer(locale, "-");
        String lang = st.nextToken();
        if(st.hasMoreTokens()) {
            String country = st.nextToken();
            return new Locale(lang, country);
        }
        else {
            return new Locale(lang);
        }
    }
}
