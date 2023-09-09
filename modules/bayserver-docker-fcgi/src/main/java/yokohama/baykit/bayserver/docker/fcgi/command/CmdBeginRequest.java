package yokohama.baykit.bayserver.docker.fcgi.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.fcgi.FcgCommand;
import yokohama.baykit.bayserver.docker.fcgi.FcgCommandHandler;
import yokohama.baykit.bayserver.docker.fcgi.FcgPacket;
import yokohama.baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;


/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * Begin request command format
 *         typedef struct {
 *             unsigned char roleB1;
 *             unsigned char roleB0;
 *             unsigned char flags;
 *             unsigned char reserved[5];
 *         } FCGI_BeginRequestBody;
 */
public class CmdBeginRequest extends FcgCommand {

    public static int FCGI_KEEP_CONN = 1;

    public static int FCGI_RESPONDER = 1;
    public static int FCGI_AUTHORIZER = 2;
    public static int FCGI_FILTER = 3;
    
    public int role;
    public boolean keepConn;

    public CmdBeginRequest(int reqId) {
        super(FcgType.BeginRequest, reqId);
    }

    @Override
    public void unpack(FcgPacket pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        super.unpack(pkt);
        role = acc.getShort();
        int flags = acc.getByte();
        keepConn = (flags & FCGI_KEEP_CONN) != 0;
    }
    @Override
    public void pack(FcgPacket pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putShort(role);
        acc.putByte(keepConn ? 1 : 0);
        byte[] reserved = new byte[5];
        acc.putBytes(reserved);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleBeginRequest(this);
    }

}
