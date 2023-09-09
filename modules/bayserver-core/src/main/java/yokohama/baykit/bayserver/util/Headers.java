package yokohama.baykit.bayserver.util;

import java.text.SimpleDateFormat;
import java.util.*;


/**
 * The class to wrap HTTP headers
 */
public class Headers {

    /**
     * Known header names
     */
    public static final String HEADER_SEPARATOR = ": ";
    public static final byte[] HEADER_SEPARATOR_BYTES = HEADER_SEPARATOR.getBytes();

    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String CONNECTION = "Connection";
    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String STATUS = "Status";
    public static final String LOCATION = "Location";
    public static final String HOST = "Host";
    public static final String COOKIE = "Cookie";
    public static final String USER_AGENT = "User-Agent";
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests";
    public static final String SERVER = "Server";
    public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    public static final String X_FORWARDED_PORT = "X-Forwarded-Port";

    /** Status */
    int status = HttpStatus.OK;

    /** Header hash */
    Map<String, List<String>> headers = new HashMap<>();

    @Override
    public String toString() {
        return "Headers(s=" + status + " h=" + headers;
    }

    public void copyTo(Headers dst) {
        dst.status = status;
        for(String name : headers.keySet()) {
            List<String> values = new ArrayList<>();
            values.addAll(headers.get(name));
            dst.headers.put(name, values);
        }
    }


    /**
     * Get the header value as string
     * 
     * @param name
     *            header name
     * @return header value
     * @throws NullPointerException
     *             If name is null
     */
    public String get(String name) {
        if (name == null)
            throw new NullPointerException();

        List<String> values = headers.get(StringUtil.toLowerCase(name));
        if(values == null)
            return null;
        return values.get(0);
    }

    /**
     * Get the header value as int
     * 
     * @param name
     *            header name
     * @return header value
     * @throws NullPointerException
     *             If name is null
     */
    public int getInt(String name) {
        String val = get(name);
        if (val == null)
            return -1;
        else
            return Integer.parseInt(val);
    }

    /**
     * Get the header value as date
     * 
     * @param name
     *            header name
     * @return header value
     * @throws NullPointerException
     *             If name is null
     */
    public long getDate(String name) {
        String val = get(name);
        if (val == null)
            return -1;

        // val is like
        //      "Friday, 30-Oct-98 05:17:54 GMT"
        //System.out.println("date str of \"" + key + "\"=" + val);
        int index1 = val.indexOf(", ");
        int index2 = val.indexOf(" GMT");
        val = val.substring(index1 + 2, index2).trim();
        return DateParser.parse(val);
    }

    /**
     * Update the header value by string
     * 
     * @param name
     *            Header Name
     * @param value
     *            Header Value
     * @throws NullPointerException
     *             If either name or value is null
     */
    public void set(String name, String value) {
        if (name == null)
            throw new NullPointerException();
        if (value == null)
            throw new NullPointerException();

        name = StringUtil.toLowerCase(name);
        List<String> values = headers.get(name);
        if(values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.clear();
        values.add(value);
    }


        /**
     * Update the header value by date
     * 
     * @param name
     *            Header Name
     * @param date
     *            Header Value
     * @throws NullPointerException
     *             If name is null
     */
    public void setDate(String name, long date) {
        Date d = new Date(date);
        String dateString = dateFormat.format(d);
        set(name, dateString);
    }

    /**
     * Update the header value by int
     * 
     * @param name
     *            Header Name
     * @param value
     *            Header Value
     * @throws NullPointerException
     *             If name or is null
     */
    public void setInt(String name, int value) {
        set(name, Integer.toString(value));
    }

    /**
     * Add a header value by string
     * 
     * @param name
     *            Header Name
     * @param value
     *            Header Value
     * @throws NullPointerException
     *             If either name or value is null
     */
    public void add(String name, String value) {
        if (name == null)
            throw new NullPointerException();
        if (value == null)
            throw new NullPointerException();

        name = StringUtil.toLowerCase(name);
        List<String> values = headers.get(name);
        if(values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);
    }

    /**
     * Add a header value by int
     * 
     * @param name
     *            Header Name
     * @param value
     *            Header Value
     * @throws NullPointerException
     *             If name is null
     */
    public void addInt(String name, int value) {
        add(name, Integer.toString(value));
    }

    /**
     * Add a header value by date
     * 
     * @param name
     *            Header Name
     * @param date
     *            Header Value
     * @throws NullPointerException
     *             If name is null
     */
    public void addDate(String name, long date) {
        Date d = new Date(date);
        String dateString = dateFormat.format(d);
        add(name, dateString);
    }

    /**
     * Get all the header name
     * 
     * @return an iterator of header names
     */
    public Collection<String> headerNames() {
        ArrayList<String> names = new ArrayList<>();
        for(String name : headers.keySet()) {
            names.add(name);
        }
        return names;
    }

    /**
     * Get all the header values of specified header name
     * 
     * @param name
     *            Header Name
     * @return an iterator of header values
     * @throws NullPointerException
     *             If name is null
     */
    public Collection<String> headerValues(String name) {
        List<String> values = headers.get(StringUtil.toLowerCase(name));
        if(values == null)
            return new ArrayList<>();
        else
            return values;
    }

    /**
     * Check the existence of header
     * 
     * @param name
     *            Header Name
     * @return true if header exists
     * @throws NullPointerException
     *             If name is null
     */
    public boolean contains(String name) {
        if (name == null)
            throw new NullPointerException();

        return headers.containsKey(StringUtil.toLowerCase(name));
    }


    public void remove(String name) {
        if (name == null)
            throw new NullPointerException();

        headers.remove(name);
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    // useful methods                                                                      //
    /////////////////////////////////////////////////////////////////////////////////////////


    public int status() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String contentType() {
        return get(CONTENT_TYPE);
    }

    public void setContentType(String s) {
        set(CONTENT_TYPE, s);
    }

    /**
     * Get content length
     *
     * @return content length. If there isn't a content length header, return
     *         -1.
     */
    public int contentLength() {
        String length = get(CONTENT_LENGTH);
        if (StringUtil.empty(length))
            return -1;
        else
            return Integer.parseInt(length);
    }

    public void setContentLength(long length) {
        set(CONTENT_LENGTH, Long.toString(length));
    }

    public ConnectionType getConnection() {
        String con = get(CONNECTION);
        return ConnectionType.getType(con);
    }


    public void clear() {
        headers.clear();
        status = HttpStatus.OK;
    }



    public enum ConnectionType {
        Close,
        KeepAlive,
        Upgrade,
        Unknown;

        static ConnectionType getType(String con) {
            if(con == null)
                return ConnectionType.Unknown;
            else if(con.equalsIgnoreCase("keep-alive"))
                return ConnectionType.KeepAlive;
            else if(con.equalsIgnoreCase("close"))
                return ConnectionType.Close;
            else if(con.equalsIgnoreCase("upgrade"))
                return ConnectionType.Upgrade;
            else
                return ConnectionType.Unknown;
        }
    }


    /**
     * Date formatter
     */
    public static SimpleDateFormat dateFormat = new SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

    /////////////////////////////////////////////////////////////////////////////////////////
    // private methods                                                                     //
    /////////////////////////////////////////////////////////////////////////////////////////

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }


}