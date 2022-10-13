package baykit.bayserver.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateParser {
    public static long parse(String dateStr) {
        Date d = null;
        for (int i = 0; i < m_formats.length; i++) {
            try {
                d = m_formats[i].parse(dateStr);
            } catch (ParseException e) {
            }
            if (d != null) {
                return d.getTime();
            }
        }
        throw new IllegalArgumentException(dateStr);
    }

    private static final SimpleDateFormat[] m_formats = {
    // RFC1123
            new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US),

            // RFC850
            new SimpleDateFormat("dd-MMM-yy HH:mm:ss", Locale.US),

            // ASCII
            new SimpleDateFormat("MMM dd HH:mm:ss yyyy", Locale.US) };

    /*
     * static { for(int i = 0;i < m_formats.length; i++) {
     * m_formats[i].setTimeZone(TimeZone.getTimeZone("GMT")); } }
     */
}

