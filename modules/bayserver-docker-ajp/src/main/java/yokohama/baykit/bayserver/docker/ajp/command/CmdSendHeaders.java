package yokohama.baykit.bayserver.docker.ajp.command;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.docker.ajp.AjpCommand;
import yokohama.baykit.bayserver.docker.ajp.AjpCommandHandler;
import yokohama.baykit.bayserver.docker.ajp.AjpPacket;
import yokohama.baykit.bayserver.docker.ajp.AjpType;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Send headers format
 *
 * AJP13_SEND_HEADERS :=
 *   prefix_code       4
 *   http_status_code  (integer)
 *   http_status_msg   (string)
 *   num_headers       (integer)
 *   response_headers *(res_header_name header_value)
 *
 * res_header_name :=
 *     sc_res_header_name | (string)   [see below for how this is parsed]
 *
 * sc_res_header_name := 0xA0 (byte)
 *
 * header_value := (string)
 */
public class CmdSendHeaders extends AjpCommand {

    static Map<String, Integer> wellKnownHeaders = new HashMap<>();
    static {
        wellKnownHeaders.put("content-type", 0xA001);
        wellKnownHeaders.put("content-language", 0xA002);
        wellKnownHeaders.put("content-length", 0xA003);
        wellKnownHeaders.put("date", 0xA004);
        wellKnownHeaders.put("last-modified", 0xA005);
        wellKnownHeaders.put("location", 0xA006);
        wellKnownHeaders.put("set-cookie", 0xA007);
        wellKnownHeaders.put("set-cookie2", 0xA008);
        wellKnownHeaders.put("servlet-engine", 0xA009);
        wellKnownHeaders.put("status", 0xA00A);
        wellKnownHeaders.put("www-authenticate", 0xA00B);
    }

    static String getWellKnownHeaderName(int code) {
        for(String name : wellKnownHeaders.keySet()) {
            if(wellKnownHeaders.get(name) == code)
                return name;
        }
        return null;
    }


    public final Map<String, ArrayList<String>> headers = new HashMap<>();
    public int status;
    public String desc;

    public CmdSendHeaders() {
        super(AjpType.SendHeaders, false);
        this.status = HttpStatus.OK;
        this.desc = null;
    }

    @Override
    public void pack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        acc.putByte(type.no);
        acc.putShort(status);
        acc.putString(HttpStatus.description(status));

        int count = 0;
        for(String name : headers.keySet()) {
            for (String value : headers.get(name)) {
                count++;
            }
        }

        acc.putShort(count);
        for(String name : headers.keySet()) {
            Integer code = wellKnownHeaders.get(name.toLowerCase());
            for (String value : headers.get(name)) {
                if (code != null) {
                    acc.putShort(code);
                } else {
                    acc.putString(name);
                }
                acc.putString(value);
            }
        }

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public void unpack(AjpPacket pkt) throws IOException {
        AjpPacket.AjpAccessor acc = pkt.newAjpDataAccessor();
        int prefixCode = acc.getByte();
        if(prefixCode != AjpType.SendHeaders.no)
            throw new ProtocolException("Expected SEND_HEADERS");
        setStatus(acc.getShort());
        setDesc(acc.getString());
        int count = acc.getShort();
        for(int i = 0; i < count; i++) {
            int code = acc.getShort();
            String name = getWellKnownHeaderName(code);
            if(name == null) {
                // code is length
                name = acc.getString(code);
            }
            String value = acc.getString();
            addHeader(name, value);
        }
    }

    @Override
    public NextSocketAction handle(AjpCommandHandler handler) throws IOException {
        return handler.handleSendHeaders(this);
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getHeader(String name) {
        ArrayList<String> values = headers.get(name.toLowerCase());
        if(values == null || values.isEmpty())
            return null;
        else
            return values.get(0);
    }
    public void addHeader(String name, String value) {
        ArrayList<String> values = headers.get(name);
        if(values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);
    }

    public int getContentLength() {
        String len = getHeader("content-length");
        if(StringUtil.empty(len))
            return -1;
        else {
            try {
                return Integer.parseInt(len);
            } catch (NumberFormatException e) {
                BayLog.error(e);
                return -1;
            }
        }
    }
}
