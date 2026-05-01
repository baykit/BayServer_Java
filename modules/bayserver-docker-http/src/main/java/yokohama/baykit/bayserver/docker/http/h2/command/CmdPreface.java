package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;

import java.io.IOException;

/**
 * Preface is dummy command and packet
 * 
 *   packet is not in frame format but raw data: "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
 */
public class CmdPreface extends H2Command {

    public static final byte[] prefaceBytes = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
    public String protocol;

    public CmdPreface(int streamId, H2Flags flags) {
        super(H2Type.Preface, streamId, flags);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        byte[] prefaceData = new byte[24];
        acc.getBytes(prefaceData);
        protocol = new String(prefaceData, 6, 8);
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        // The H2 client connection preface is 24 raw bytes that MUST appear
        // at the very start of the connection — it is NOT wrapped in an H2
        // frame (RFC 7540 § 3.5). H2Packet reserves 9 bytes at buf[0..9] for
        // the frame header and writes payload to buf[9..]; using
        // newDataAccessor here would produce 9 zero bytes followed by the
        // preface, which servers correctly reject (the leading zeros
        // misparse as a frame with length=0, type=0, which then reads "PRI"
        // as the next frame's length and trips MAX_FRAME_SIZE).
        //
        // Bypass the frame-header reserve by writing the preface bytes
        // directly into buf[0..24] and setting bufLen=24.
        while (pkt.buf.length < prefaceBytes.length) {
            pkt.expand();
        }
        System.arraycopy(prefaceBytes, 0, pkt.buf, 0, prefaceBytes.length);
        pkt.bufLen = prefaceBytes.length;
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handlePreface(this);
    }
}
