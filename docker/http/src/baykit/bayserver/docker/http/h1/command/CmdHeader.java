package baykit.bayserver.docker.http.h1.command;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.Constants;
import baykit.bayserver.Symbol;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.docker.http.h1.H1Command;
import baykit.bayserver.docker.http.h1.H1CommandHandler;
import baykit.bayserver.docker.http.h1.H1Packet;
import baykit.bayserver.docker.http.h1.H1Type;
import baykit.bayserver.util.Headers;
import baykit.bayserver.util.HttpStatus;

import java.io.IOException;
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

    public CmdHeader(boolean req) {
        super(H1Type.Header);
        this.req = req;
    }

    public static CmdHeader newReqHeader(String method, String uri, String version) {
        CmdHeader h = new CmdHeader(true);
        h.method = method;
        h.uri = uri;
        h.version = version;
        return h;
    }


    public static CmdHeader newResHeader(Headers headers, String version) {
        CmdHeader h = new CmdHeader(false);
        h.version = version;
        h.status = headers.status();
        for(String name : headers.headerNames()) {
            for(String value : headers.headerValues(name)) {
                h.addHeader(name, value);
            }
        }
        return h;
    }

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


    /******************************************************************************************/
    /**  Private methods                                                                      */
    /******************************************************************************************/


    private void unpackRequestLine(byte[] buf, int start, int len) throws IOException {
        String line = new String(buf, start, len);
        StringTokenizer st = new StringTokenizer(line);

        try {
            method = st.nextToken();
            uri = st.nextToken();
            version = st.nextToken();
        } catch (NoSuchElementException e) {
            throw new ProtocolException(
                    BayMessage.get(Symbol.HTP_INVALID_FIRST_LINE, line));
        }
    }

    private void unpackStatusLine(byte[] buf, int start, int len) throws IOException {
        String line = new String(buf, start, len);
        StringTokenizer st = new StringTokenizer(line);

        try {
            version = st.nextToken();
            String status = st.nextToken();
            this.status = Integer.parseInt(status);

        } catch (Exception e) {
            throw new IOException(
                    BayMessage.get(Symbol.HTP_INVALID_FIRST_LINE, line));
        }
    }

    private void unpackMessageHeader(byte[] bytes, int start, int len) throws IOException {
        char[] buf = new char[len];
        boolean readName = true;
        int pos = 0;
        boolean skipping = true;
        String name = null, value;
        for(int i = 0; i < len; i++) {
            int b = bytes[start + i];
            if(skipping && Character.isWhitespace(b))
                continue;
            else if(readName && (b == ':')) {
                name = new String(buf, 0, pos);
                pos = 0;
                skipping = true;
                readName = false;
            }
            else {
                if(readName) {
                    // make the case of header name be lower force
                    buf[pos++] = Character.toLowerCase((char)b);
                }
                else {
                    buf[pos++] = (char) b;
                }
                skipping = false;
            }
        }

        if (name == null) {
            BayLog.debug("Invalid message header: %s", new String(bytes, start, len));
            throw new ProtocolException(
                    BayMessage.get(Symbol.HTP_INVALID_HEADER_FORMAT, ""));
        }

        value = new String(buf, 0, pos);

        addHeader(name, value);
        //BayServer.debug(this + " receive header: " + name + "=" + value);
        //if(BayLog.isTraceMode()) {
        //    BayLog.trace(this + " receive header: " + name + "=" + value);
        //}
    }


    private void packRequestLine(PacketPartAccessor acc) throws IOException {
        acc.putString(method);
        acc.putBytes(Constants.SPACE_BYTES);
        acc.putString(uri);
        acc.putBytes(Constants.SPACE_BYTES);
        acc.putString(version);
        acc.putBytes(Constants.CRLF_BYTES);
    }

    private void packStatusLine(PacketPartAccessor acc) throws IOException {
        String desc = HttpStatus.description(status);

        if (version != null && version.equalsIgnoreCase("HTTP/1.1"))
            acc.putBytes(Constants.HTTP_11_BYTES);
        else
            acc.putBytes(Constants.HTTP_10_BYTES);

        // status
        acc.putBytes(H1Packet.SP_BYTES);
        acc.putString(Integer.toString(status));
        acc.putBytes(H1Packet.SP_BYTES);
        acc.putString(desc);
        acc.putBytes(H1Packet.CRLF_BYTES);
    }

    public void packMessageHeader(PacketPartAccessor acc, String name, String value) throws IOException {
        //BayServer.debug("pack header :" + name + "=" + value);
        acc.putString(name);
        acc.putBytes(Headers.HEADER_SEPARATOR_BYTES);
        acc.putString(value);
        acc.putBytes(Constants.CRLF_BYTES);
    }

    public void packEndHeader(PacketPartAccessor acc) throws IOException {
        acc.putBytes(Constants.CRLF_BYTES);
    }
}
