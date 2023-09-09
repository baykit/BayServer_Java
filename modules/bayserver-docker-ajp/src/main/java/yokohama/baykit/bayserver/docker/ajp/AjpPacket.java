package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.Packet;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;

/**
 * AJP Protocol
 * https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 *
 * AJP packet spec
 *
 *   packet:  preamble, length, body
 *   preamble:
 *        0x12, 0x34  (client->server)
 *     | 'A', 'B'     (server->client)
 *   length:
 *      2 byte
 *   body:
 *      $length byte
 *
 *
 *  Body format
 *    client->server
 *    Code     Type of Packet    Meaning
 *       2     Forward Request   Begin the request-processing cycle with the following data
 *       7     Shutdown          The web server asks the container to shut itself down.
 *       8     Ping              The web server asks the container to take control (secure login phase).
 *      10     CPing             The web server asks the container to respond quickly with a CPong.
 *    none     Data              Size (2 bytes) and corresponding body data.
 *
 *    server->client
 *    Code     Type of Packet    Meaning
 *       3     Send Body Chunk   Send a chunk of the body from the servlet container to the web server (and presumably, onto the browser).
 *       4     Send Headers      Send the response headers from the servlet container to the web server (and presumably, onto the browser).
 *       5     End Response      Marks the end of the response (and thus the request-handling cycle).
 *       6     Get Body Chunk    Get further data from the request if it hasn't all been transferred yet.
 *       9     CPong Reply       The reply to a CPing request
 *
 */
public class AjpPacket extends Packet<AjpType> {

    public class AjpAccessor extends PacketPartAccessor {

        public AjpAccessor(Packet pkt, int start, int maxLen) {
            super(pkt, start, maxLen);
        }

        @Override
        public void putString(String str) throws IOException {
            if (StringUtil.empty(str)) {
                putShort(0xffff);
            } else {
                putShort(str.length());
                super.putString(str);
                putByte(0); // null terminator
            }
        }

        public String getString() throws IOException {
            return getString(getShort());
        }

        public String getString(int len) throws IOException {

            if (len == 0xffff) {
                return "";
            }

            byte[] buf = new byte[len];
            getBytes(buf);
            getByte(); // null terminator

            return new String(buf);
        }
    }


    public static final int PREAMBLE_SIZE = 4;
    public static final int MAX_DATA_LEN = 8192 - PREAMBLE_SIZE;
    public static final int MIN_BUF_SIZE = 1024;

    public boolean toServer;

    AjpPacket(AjpType type) {
        super(type, PREAMBLE_SIZE, MAX_DATA_LEN);
    }

    @Override
    public void reset() {
        toServer = false;
        super.reset();
    }

    @Override
    public String toString() {
        return "AjpPacket(" + type.name() + ")";
    }

    public AjpAccessor newAjpHeaderAccessor() {
        return new AjpAccessor(this, 0, PREAMBLE_SIZE);
    }

    public AjpAccessor newAjpDataAccessor() {
        return new AjpAccessor(this, PREAMBLE_SIZE, -1);
    }
}
