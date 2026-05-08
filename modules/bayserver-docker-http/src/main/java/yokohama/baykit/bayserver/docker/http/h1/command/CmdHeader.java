package yokohama.baykit.bayserver.docker.http.h1.command;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h1.H1Command;
import yokohama.baykit.bayserver.docker.http.h1.H1CommandHandler;
import yokohama.baykit.bayserver.docker.http.h1.H1Packet;
import yokohama.baykit.bayserver.docker.http.h1.H1Type;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.ByteArrayUtil;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * Header format
 *
 *
 *        generic-message = start-line
 *                           *(message-header CRLF)
 *                           CRLF
 *                           [ message-body ]
 *        start-line      = Request-Line | Status-Line
 *
 *
 *        message-header = field-name ":" [ field-value ]
 *        field-name     = token
 *        field-value    = *( field-content | LWS )
 *        field-content  = <the OCTETs making up the field-value
 *                         and consisting of either *TEXT or combinations
 *                         of token, separators, and quoted-string>
 */
public class CmdHeader extends H1Command {

    enum State {
        ReadFirstLine,
        ReadMessageHeaders,
    }


    public ArrayList<String[]> headers = new ArrayList<>();
    boolean req; // request packet
    public String method, uri, version;
    public int status;

    /** Line separator */
    public static String CRLF = "\r\n";
    public static byte[] CRLF_BYTES = CRLF.getBytes();

    /** Bytes of Space */
    public static String SPACE = " ";
    public static byte[] SPACE_BYTES = SPACE.getBytes();

    /** HTTP 1.1 Protocol header bytes */
    public static String HTTP_11 = "HTTP/1.1";
    public static byte[] HTTP_11_BYTES = HTTP_11.getBytes();

    /** HTTP 1.0 Protocol header bytes */
    public static String HTTP_10 = "HTTP/1.0";
    public static byte[] HTTP_10_BYTES = HTTP_10.getBytes();

    private static final byte[] H11_200 = "HTTP/1.1 200 OK\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] H10_200 = "HTTP/1.0 200 OK\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public CmdHeader() {
        super(H1Type.Header);
    }

    public void init(boolean req) {
        this.req = req;
    }

    public void initReqHeader(String method, String uri, String version) {
        this.req = true;
        this.method = method;
        this.uri = uri;
        this.version = version;
    }


    public void initResHeader(Headers headers, String version) {
        this.req = false;
        this.version = version;
        this.status = headers.status();
        for(String name : headers.headerNames()) {
            for(String value : headers.headerValues(name)) {
                this.addHeader(name, value);
            }
        }
    }

    ///////////////////////////////////////////////
    // Implements Reusable
    ///////////////////////////////////////////////

    @Override
    public void reset() {
        this.req = false;
        this.method = null;
        this.uri = null;
        this.version = null;
        headers.clear();
    }

    ///////////////////////////////////////////////
    // Implements Command
    ///////////////////////////////////////////////

    @Override
    public void unpack(H1Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        int pos = 0, dataLen = pkt.dataLen();
        State state = State.ReadFirstLine;

        int lineStartPos = 0;
        int lineLen = 0;

        loop:
        for (pos = 0; pos < dataLen; pos++) {
            int b = acc.getByte();
            switch(b) {
                case '\r':
                    continue;

                case '\n':
                    if (lineLen == 0)
                        break loop;
                    if (state == State.ReadFirstLine) {
                        if (req) {
                            unpackRequestLine(pkt.buf, lineStartPos, lineLen);
                        }
                        else {
                            unpackStatusLine(pkt.buf, lineStartPos, lineLen);
                        }
                        state = State.ReadMessageHeaders;
                    }
                    else {
                        unpackMessageHeader(pkt.buf, lineStartPos, lineLen);
                    }
                    lineLen = 0;
                    lineStartPos = pos + 1;
                    break;

                default:
                    lineLen++;
            }
        }

        if(state == State.ReadFirstLine) {
            throw new ProtocolException("Invalid HTTP header format: " + new String(pkt.buf, 0, dataLen));
        }
    }

    @Override
    public void pack(H1Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        if(req) {
            packRequestLine(acc);
        }
        else {
            packStatusLine(acc);
        }
        for(String[] nv: headers) {
            packMessageHeader(acc, nv[0], nv[1]);
        }
        packEndHeader(acc);
    }

    @Override
    public NextSocketAction handle(H1CommandHandler handler) throws IOException {
        return handler.handleHeader(this);
    }

    ///////////////////////////////////////////////
    // Custom methods
    ///////////////////////////////////////////////

    public void addHeader(String name, String value) {
        if(value == null) {
            BayLog.warn("Header value is null: " + name);
        }
        else {
            headers.add(new String[]{name, value});
        }
    }

    public void setHeader(String name, String value) {
        if(value == null) {
            BayLog.warn("Header value is null: " + name);
            return;
        }
        for(String[] nv : headers) {
            if (nv[0].equalsIgnoreCase(name)) {
                nv[1] = value;
                return;
            }
        }
        headers.add(new String[]{name, value});
    }


    /******************************************************************************************/
    /**  Private methods                                                                      */
    /******************************************************************************************/


    private void unpackRequestLine(byte[] buf, int start, int len) throws IOException {
        int end = start + len;

        // find first space
        int sp1 = ByteArrayUtil.indexOf(buf, start, end, (byte) ' ');
        if (sp1 < 0)
            throw invalidFirstLine(buf, start, len);

        // find second space (skip consecutive spaces just in case)
        int i = sp1 + 1;
        while (i < end && buf[i] == ' ') i++;
        int sp2 = ByteArrayUtil.indexOf(buf, i, end, (byte) ' ');
        if (sp2 < 0)
            throw invalidFirstLine(buf, start, len);

        // skip spaces before version
        int j = sp2 + 1;
        while (j < end && buf[j] == ' ') j++;
        if (j >= end)
            throw invalidFirstLine(buf, start, len);

        method = new String(buf, start, sp1 - start, StandardCharsets.US_ASCII);
        uri = new String(buf, i, sp2 - i, StandardCharsets.US_ASCII); // 方針次第で UTF_8
        version = new String(buf, j, end - j, StandardCharsets.US_ASCII);
    }

    private ProtocolException invalidFirstLine(byte[] buf, int start, int len) {
        String line = new String(buf, start, len, StandardCharsets.US_ASCII);
        return new ProtocolException(BayMessage.get(Symbol.HTP_INVALID_FIRST_LINE, line));
    }

    private void unpackStatusLine(byte[] buf, int start, int len) throws IOException {
        int end = start + len;

        // Find the space after "HTTP/1.x " (end of the HTTP version)
        int sp1 = ByteArrayUtil.indexOf(buf, start, end, (byte) ' ');
        if (sp1 < 0) throw invalidStatusLine(buf, start, len);

        // Skip consecutive spaces
        int i = sp1 + 1;
        while (i < end && buf[i] == ' ') i++;

        // HTTP status code is usually 3 digits
        if (i + 2 >= end) throw invalidStatusLine(buf, start, len);
        int s0 = buf[i] - '0';
        int s1 = buf[i + 1] - '0';
        int s2 = buf[i + 2] - '0';
        if ((s0 | s1 | s2) < 0 || s0 > 9 || s1 > 9 || s2 > 9)
            throw invalidStatusLine(buf, start, len);

        this.status = s0 * 100 + s1 * 10 + s2;

        // Create the version string here only if needed (ASCII only, no StringTokenizer)
        this.version = new String(buf, start, sp1 - start, StandardCharsets.US_ASCII);
    }

    private IOException invalidStatusLine(byte[] buf, int start, int len) {
        String line = new String(buf, start, len, StandardCharsets.US_ASCII);
        return new IOException(BayMessage.get(Symbol.HTP_INVALID_FIRST_LINE, line));
    }

    private void unpackMessageHeader(byte[] bytes, int start, int len) throws IOException {
        // ASCII / Latin-1 fast path on a reusable byte[] scratch.
        // The previous loop went through Character.toLowerCase (Unicode
        // fold) on every byte and allocated a fresh char[len] per call.
        if (parseScratch == null || parseScratch.length < len) {
            parseScratch = new byte[Math.max(len, 64)];
        }
        byte[] buf = parseScratch;
        boolean readName = true;
        int pos = 0;
        boolean skipping = true;
        int colonPos = -1;
        for (int i = 0; i < len; i++) {
            int b = bytes[start + i] & 0xff;
            if (skipping && (b == ' ' || b == '\t'))
                continue;
            if (readName && b == ':') {
                colonPos = pos;
                readName = false;
                skipping = true;
                continue;
            }
            if (readName) {
                if (b >= 'A' && b <= 'Z') b += 32;
                buf[pos++] = (byte) b;
            } else {
                buf[pos++] = (byte) b;
            }
            skipping = false;
        }

        if (colonPos < 0) {
            BayLog.debug("Invalid message header: %s", new String(bytes, start, len));
            throw new ProtocolException(
                    BayMessage.get(Symbol.HTP_INVALID_HEADER_FORMAT, ""));
        }

        String name = new String(buf, 0, colonPos, StandardCharsets.ISO_8859_1);
        String value = new String(buf, colonPos, pos - colonPos, StandardCharsets.ISO_8859_1);

        addHeader(name, value);
    }

    /** Reusable scratch buffer for header-name/value byte assembly. */
    private byte[] parseScratch;


    private void packRequestLine(PacketPartAccessor acc) throws IOException {
        acc.putString(method);
        acc.putBytes(SPACE_BYTES);
        acc.putString(uri);
        acc.putBytes(SPACE_BYTES);
        acc.putString(version);
        acc.putBytes(CRLF_BYTES);
    }

    private void packStatusLine(PacketPartAccessor acc) throws IOException {
        if(status == 200) {
            if (version != null && version.equalsIgnoreCase("HTTP/1.1"))
                acc.putBytes(H11_200);
            else
                acc.putBytes(H10_200);
        }
        else {
            String desc = HttpStatus.description(status);

            if (version != null && version.equalsIgnoreCase("HTTP/1.1"))
                acc.putBytes(HTTP_11_BYTES);
            else
                acc.putBytes(HTTP_10_BYTES);

            // status
            acc.putBytes(H1Packet.SP_BYTES);
            acc.putString(Integer.toString(status));
            acc.putBytes(H1Packet.SP_BYTES);
            acc.putString(desc);
            acc.putBytes(H1Packet.CRLF_BYTES);
        }
    }

    public void packMessageHeader(PacketPartAccessor acc, String name, String value) throws IOException {
        //BayServer.debug("pack header :" + name + "=" + value);
        acc.putString(name);
        acc.putBytes(Headers.HEADER_SEPARATOR_BYTES);
        acc.putString(value);
        acc.putBytes(CRLF_BYTES);
    }

    public void packEndHeader(PacketPartAccessor acc) throws IOException {
        acc.putBytes(CRLF_BYTES);
    }
}
