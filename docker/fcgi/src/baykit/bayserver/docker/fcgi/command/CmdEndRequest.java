package baykit.bayserver.docker.fcgi.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.docker.fcgi.FcgCommand;
import baykit.bayserver.docker.fcgi.FcgCommandHandler;
import baykit.bayserver.docker.fcgi.FcgPacket;
import baykit.bayserver.docker.fcgi.FcgType;

import java.io.IOException;

/**
 * FCGI spec
 *   http://www.mit.edu/~yandros/doc/specs/fcgi-spec.html
 *
 * Endrequest command format
 *         typedef struct {
 *             unsigned char appStatusB3;
 *             unsigned char appStatusB2;
 *             unsigned char appStatusB1;
 *             unsigned char appStatusB0;
 *             unsigned char protocolStatus;
 *             unsigned char reserved[3];
 *         } FCGI_EndRequestBody;
 */
public class CmdEndRequest extends FcgCommand {

    public static final int FCGI_REQUEST_COMPLETE = 0;
    public static final int FCGI_CANT_MPX_CONN = 1;
    public static final int FCGI_OVERLOADED = 2;
    public static final int FCGI_UNKNOWN_ROLE = 3;

    int appStatus;
    int protocolStatus = FCGI_REQUEST_COMPLETE;

    public CmdEndRequest(int reqId) {
        super(FcgType.EndRequest, reqId);
    }

    @Override
    public void unpack(FcgPacket pkt) throws IOException {
        super.unpack(pkt);
        PacketPartAccessor acc = pkt.newDataAccessor();
        appStatus = acc.getInt();
        protocolStatus = acc.getByte();
    }

    public void pack(FcgPacket pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putInt(appStatus);
        acc.putByte(protocolStatus);
        byte[] reserved = new byte[3];
        acc.putBytes(reserved);

        // must be called from last line
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(FcgCommandHandler handler) throws IOException {
        return handler.handleEndRequest(this);
    }
}
