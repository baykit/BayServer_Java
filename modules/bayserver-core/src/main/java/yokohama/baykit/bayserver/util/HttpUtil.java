package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.tour.Tour;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.util.Iterator;
import java.util.regex.Pattern;

public class HttpUtil {

    static final int MAX_LINE_LEN = 5000;

    /** HTTP 1.1 Protocol header bytes */
    public static String HTTP_11 = "HTTP/1.1";
    public static byte[] HTTP_11_BYTES = HTTP_11.getBytes();

    /** HTTP 1.0 Protocol header bytes */
    public static String HTTP_10 = "HTTP/1.0";
    public static byte[] HTTP_10_BYTES = HTTP_10.getBytes();

    /**
     * Read a line from stream
     *
     * @return line as string
     */
    public static String readLine(InputStream in) throws IOException {

        /** Current reading line */
        char[] buf = new char[MAX_LINE_LEN];

        int n;
        int c = 0;
        for (n = 0; n < buf.length; n++) {

            c = in.read();

            if (c == -1)
                break;

            // If character is newline, end to read line
            if (c == '\n') {
                break;
            }

            // Put the character to buffer
            buf[n] = (char) c;
        }

        if(n == 0 && c == -1)
            return null;

        // If line is too long, return error
        if (n == buf.length) {
            throw new IOException("Request line too long");
        }

        // Remove a '\r' character
        if (n != 0 && buf[n - 1] == '\r')
            n--;

        // Create line as string
        return new String(buf, 0, n);
    }

    /**
     * Parse message headers
     *
     * <pre>
     *
     *
     *     message-header = field-name &quot;:&quot; [field-value]
     *
     *
     * </pre>
     */
    public static void parseMessageHeaders(InputStream in, Headers hdr) throws IOException {

        while (true) {

            // Read line
            String line = HttpUtil.readLine(in);

            // if line is empty ("\r\n")
            // finish reading.
            if (StringUtil.empty(line)) {
                break;
            }

            int pos = line.indexOf(':');
            if (pos != -1) {
                String key = line.substring(0, pos);
                String value = line.substring(pos + 1).trim();
                hdr.add(key, value);
                //BayLog.trace("parse header: " + key + "=" + value);
            }
        }
    }

    /**
     * Send MIME headers This method is called from sendHeaders()
     */
    public static void sendMimeHeaders(Headers hdr, OutputStream out) throws IOException {

        // headers
        for (String name : hdr.headerNames()) {
            Iterator<String> values = hdr.headerValues(name).iterator();
            //name = name.substring(0, 1).toUpperCase() + name.substring(1);
            while (values.hasNext()) {
                String value = values.next();
                out.write(name.getBytes());
                out.write(Headers.HEADER_SEPARATOR_BYTES);
                out.write(value.getBytes());
                out.write(CharUtil.CRLF_BYTES);
            }
        }
    }


    /**
     * Send status header. This method is called from sendHeaders()
     */
    public static void sendStatusHeader(Tour tur, OutputStream out) throws IOException {
        String desc = HttpStatus.description(tur.res.headers.status());

        if (tur.req.protocol != null && tur.req.protocol.equalsIgnoreCase("HTTP/1.1"))
            out.write(HTTP_11_BYTES);
        else
            out.write(HTTP_10_BYTES);

        // status
        out.write(' ');
        out.write(Integer.toString(tur.res.headers.status()).getBytes());
        out.write(' ');
        out.write(desc.getBytes());
        out.write(CharUtil.CRLF_BYTES);
    }

    public static void sendNewLine(OutputStream out) throws IOException {
        out.write(CharUtil.CRLF_BYTES);
    }

    public static String resolveHost(String adr) {
        try {
            InetAddress sadr = InetAddress.getByName(adr);
            if (sadr == null)
                return null;
            else
                return sadr.getHostName();
        } catch (IOException e) {
            BayLog.warn(e, "Cannot resolve host name: %s", e);
            return null;
        }
    }


    public static void checkUri(String uri) throws ProtocolException {
        if (uri.indexOf('\0') != -1) {
            throw new ProtocolException("path contains null byte");
        }

        for (int i = 0; i < uri.length(); i++) {
            char ch = uri.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw new ProtocolException("path contains control character");
            }
        }
    }

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("^[A-Za-z0-9!#$%&'*+\\-.^_`|~]+$");

    public static void checkMethod(String method) throws ProtocolException{
        if (!TOKEN_PATTERN.matcher(method).matches())
            throw new ProtocolException("Invalid method: " + method);

    }

}
