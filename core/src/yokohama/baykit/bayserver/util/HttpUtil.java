package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Constants;
import yokohama.baykit.bayserver.tour.Tour;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtil {

    static final int MAX_LINE_LEN = 5000;

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
                out.write(Constants.CRLF_BYTES);
            }
        }
    }


    /**
     * Send status header. This method is called from sendHeaders()
     */
    public static void sendStatusHeader(Tour tur, OutputStream out) throws IOException {
        String desc = HttpStatus.description(tur.res.headers.status());

        if (tur.req.protocol != null && tur.req.protocol.equalsIgnoreCase("HTTP/1.1"))
            out.write(Constants.HTTP_11.getBytes());
        else
            out.write(Constants.HTTP_10.getBytes());

        // status
        out.write(' ');
        out.write(Integer.toString(tur.res.headers.status()).getBytes());
        out.write(' ');
        out.write(desc.getBytes());
        out.write(Constants.CRLF_BYTES);
    }

    public static void sendNewLine(OutputStream out) throws IOException {
        out.write(Constants.CRLF.getBytes());
    }

    /**
     * Parse AUTHORIZATION header
     * @param tur
     */
    public static void parseAuthrization(Tour tur) {
        String auth = tur.req.headers.get(Headers.AUTHORIZATION);
        if (!StringUtil.empty(auth)) {
            Pattern ptn = Pattern.compile("Basic (.*)");
            Matcher mch = ptn.matcher(auth);
            if (!mch.matches()) {
                BayLog.warn("Not matched with basic authentication format");
            } else {
                auth = mch.group(1);
                try {
                    auth = new String(Base64.getDecoder().decode(auth));
                    ptn = Pattern.compile("(.*):(.*)");
                    mch = ptn.matcher(auth);
                    if (mch.matches()) {
                        tur.req.remoteUser = mch.group(1);
                        tur.req.remotePass = mch.group(2);
                    }
                } catch (Exception e) {
                    BayLog.error(e);
                }
            }
        }
    }

    public static void parseHostPort(Tour tour, int defaultPort) {
        tour.req.reqHost = "";

        String hostPort = tour.req.headers.get(Headers.X_FORWARDED_HOST);
        if(StringUtil.isSet(hostPort)) {
            tour.req.headers.remove(Headers.X_FORWARDED_HOST);
            tour.req.headers.set(Headers.HOST, hostPort);
        }

        hostPort = tour.req.headers.get(Headers.HOST);
        if(StringUtil.isSet(hostPort)) {
            int pos = hostPort.lastIndexOf(':');
            if(pos == -1) {
                tour.req.reqHost = hostPort;
                tour.req.reqPort = defaultPort;
            }
            else {
                tour.req.reqHost = hostPort.substring(0, pos);
                try {
                    tour.req.reqPort = Integer.parseInt(hostPort.substring(pos + 1));
                }
                catch(NumberFormatException e) {
                    BayLog.error(e);
                }
            }
        }
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

}
