package yokohama.baykit.bayserver.docker.ajp.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.docker.ajp.AjpCommand;
import yokohama.baykit.bayserver.docker.ajp.AjpCommandHandler;
import yokohama.baykit.bayserver.docker.ajp.AjpPacket;
import yokohama.baykit.bayserver.docker.ajp.AjpType;
import yokohama.baykit.bayserver.util.Headers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * AJP protocol
 *    https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 *
 * AJP13_FORWARD_REQUEST :=
 *     prefix_code      (byte) 0x02 = JK_AJP13_FORWARD_REQUEST
 *     method           (byte)
 *     protocol         (string)
 *     req_uri          (string)
 *     remote_addr      (string)
 *     remote_host      (string)
 *     server_name      (string)
 *     server_port      (integer)
 *     is_ssl           (boolean)
 *     num_headers      (integer)
 *     request_headers *(req_header_name req_header_value)
 *     attributes      *(attribut_name attribute_value)
 *     request_terminator (byte) OxFF
 */
public class CmdForwardRequest extends AjpCommand {

    static HashMap<Integer, String> methods = new HashMap<>();
    static {
        methods.put(1, "OPTIONS");
        methods.put(2, "GET");
        methods.put(3, "HEAD");
        methods.put(4, "POST");
        methods.put(5, "PUT");
        methods.put(6, "DELETE");
        methods.put(7, "TRACE");
        methods.put(8, "PROPFIND");
        methods.put(9, "PROPPATCH");
        methods.put(10, "MKCOL");
        methods.put(11, "COPY");
        methods.put(12, "MOVE");
        methods.put(13, "LOCK");
        methods.put(14, "UNLOCK");
        methods.put(15, "ACL");
        methods.put(16, "REPORT");
        methods.put(17, "VERSION_CONTROL");
        methods.put(18, "CHECKIN");
        methods.put(19, "CHECKOUT");
        methods.put(20, "UNCHECKOUT");
        methods.put(21, "SEARCH");
        methods.put(22, "MKWORKSPACE");
        methods.put(23, "UPDATE");
        methods.put(24, "LABEL");
        methods.put(25, "MERGE");
        methods.put(26, "BASELINE_CONTROL");
        methods.put(27, "MKACTIVITY");
    }
    
    static int getMethodCode(String method) {
        for(int code : methods.keySet()) {
            if(methods.get(code).equalsIgnoreCase(method))
                return code;
        }
        return -1;
    }

    static HashMap<Integer, String> wellKnownHeaders = new HashMap<>();
    static {
        wellKnownHeaders.put(0xA001, "Accept");
        wellKnownHeaders.put(0xA002, "Accept-Charset");
        wellKnownHeaders.put(0xA003, "Accept-Encoding");
        wellKnownHeaders.put(0xA004, "Accept-Language");
        wellKnownHeaders.put(0xA005, "Authorization");
        wellKnownHeaders.put(0xA006, "Connection");
        wellKnownHeaders.put(0xA007, "Content-Type");
        wellKnownHeaders.put(0xA008, "Content-Length");
        wellKnownHeaders.put(0xA009, "Cookie");
        wellKnownHeaders.put(0xA00A, "Cookie2");
        wellKnownHeaders.put(0xA00B, "Host");
        wellKnownHeaders.put(0xA00C, "Pragma");
        wellKnownHeaders.put(0xA00D, "Referer");
        wellKnownHeaders.put(0xA00E, "User-Agent");
    }

    static int getWellKnownHeaderCode(String name) {
        for(int code : wellKnownHeaders.keySet()) {
            if(wellKnownHeaders.get(code).equalsIgnoreCase(name))
                return code;
        }
        return -1;
    }

    static HashMap<Integer, String> attributeNames = new HashMap<>();
    static {
        attributeNames.put(0x01, "?context");
        attributeNames.put(0x02, "?servlet_path");
        attributeNames.put(0x03, "?remote_user");
        attributeNames.put(0x04, "?auth_type");
        attributeNames.put(0x05, "?query_string");
        attributeNames.put(0x06, "?route");
        attributeNames.put(0x07, "?ssl_cert");
        attributeNames.put(0x08, "?ssl_cipher");
        attributeNames.put(0x09, "?ssl_session");
        attributeNames.put(0x0A, "?req_attribute");
        attributeNames.put(0x0B, "?ssl_key_size");
        attributeNames.put(0x0C, "?secret");
        attributeNames.put(0x0D, "?stored_method");
    }

    static int getAttributeCode(String atr) {
        for(int code : attributeNames.keySet()) {
            if(attributeNames.get(code).equalsIgnoreCase(atr))
                return code;
        }
        return -1;
    }

    public String method;
    public String protocol;
    public String reqUri;
    public String remoteAddr;
    public String remoteHost;
    public String serverName;
    public int serverPort;
    public boolean isSsl;
    public Headers headers = new Headers();
    public final Map<String, String> attributes = new HashMap<>();

    public CmdForwardRequest() {
        super(AjpType.ForwardRequest, true);
    }


    @Override
    public String toString() {
        String s = "ForwardRequest(m=" + method + " p=" + protocol + " u=" + reqUri + " ra=" + remoteAddr + " rh=" + remoteHost + " sn=" + serverName;
        s += " sp=" + serverPort + " ss=" + isSsl + " h=" + headers;
        return s;
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        //BayLog.info("%s", this);
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.putByte(type.no); // prefix code
        acc.putByte(getMethodCode(method));
        acc.putString(protocol);
        acc.putString(reqUri);
        acc.putString(remoteAddr);
        acc.putString(remoteHost);
        acc.putString(serverName);
        acc.putShort(serverPort);
        acc.putByte(isSsl ? 1 : 0);
        writeRequestHeaders(acc);
        writeAttributes(acc);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        super.unpack(pkt);
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.getByte(); // prefix code
        method = methods.get(acc.getByte());
        protocol = acc.getString();
        reqUri = acc.getString();
        remoteAddr = acc.getString();
        remoteHost = acc.getString();
        serverName = acc.getString();
        serverPort = acc.getShort();
        isSsl = acc.getByte() == 1;
        //BayLog.debug("ForwardRequest: uri=" + reqUri);

        readRequestHeaders(acc);
        readAttributes(acc);
        //BayLog.info("%s", this);
    }

    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleForwardRequest(this);
    }

    private void readRequestHeaders(AjpPacket.AjpAccessor acc) throws IOException {
        int count = acc.getShort();
        for (int i = 0; i < count; i++) {
            int code = acc.getShort();
            String name;
            if (code >= 0xA000) {
                name = wellKnownHeaders.get(code);
                if (name == null)
                    throw new ProtocolException("Invalid header");
            } else {
                name = acc.getString(code);
            }
            String value = acc.getString();
            headers.add(name, value);
            //BayLog.debug("ajp: ForwardRequest header:" + name + ":" + value);
        }
    }

    private void readAttributes(AjpPacket.AjpAccessor acc) throws IOException {
        while (true) {
            int code = acc.getByte();
            //BayLog.debug("ajp: ForwardRequest readAttributes: code=" + Integer.toHexString(code));
            String name;
            if (code == 0xFF) {
                break;
            } else if (code == 0x0A) {
                name = acc.getString();
            } else {
                name = attributeNames.get(code);
                if (name == null)
                    throw new ProtocolException("Invalid attribute: code=" + code);
            }

            if (code == 0x0B) { // "?ssl_key_size"
                int value = acc.getShort();
                attributes.put(name, Integer.toString(value));
            }
            else {
                String value = acc.getString();
                attributes.put(name, value);
            }
            //BayLog.debug("ajp: ForwardRequest readAttributes:" + name + ":" + value);
        }
    }

    private void writeRequestHeaders(AjpPacket.AjpAccessor acc) {
        ArrayList<String[]> hlist = new ArrayList<>();
        for(String name : headers.headerNames()) {
            for(String value : headers.headerValues(name)) {
                hlist.add(new String[]{name, value});
            }
        }
        acc.putShort(hlist.size());
        for (String[] hdr : hlist) {
            int code = getWellKnownHeaderCode(hdr[0]);
            if(code != -1) {
                acc.putShort(code);
            }
            else {
                acc.putString(hdr[0]);
            }
            acc.putString(hdr[1]);
            //BayServer.debug("ForwardRequest header:" + name + ":" + value);
        }
    }

    private void writeAttributes(AjpPacket.AjpAccessor acc) {
        for(String name : attributes.keySet()) {
            String value = attributes.get(name);
            int code = getAttributeCode(name);
            if(code != -1) {
                acc.putByte(code);
            }
            else {
                acc.putString(name);
            }
            acc.putString(value);
        }
        acc.putByte(0xFF); // terminator code
    }

}
