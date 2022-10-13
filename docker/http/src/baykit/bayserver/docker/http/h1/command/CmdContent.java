package baykit.bayserver.docker.http.h1.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.docker.http.h1.H1Command;
import baykit.bayserver.docker.http.h1.H1CommandHandler;
import baykit.bayserver.docker.http.h1.H1Packet;
import baykit.bayserver.docker.http.h1.H1Type;

import java.io.IOException;

public class CmdContent extends H1Command {
    public byte[] buffer;
    public int start;
    public int len;
    
    public CmdContent() {
        this(null, 0, 0);
    }

    public CmdContent(byte[] buf, int start, int len) {
        super(H1Type.Content);
        this.buffer = buf;
        this.start = start;
        this.len = len;
    }

    @Override
    public void unpack(H1Packet pkt) {
        buffer = pkt.buf;
        start = pkt.headerLen;
        len = pkt.dataLen();
    }

    @Override
    public void pack(H1Packet pkt) throws IOException {
        PacketPartAccessor acc = pkt.newDataAccessor();
        acc.putBytes(buffer, start, len);
    }

    @Override
    public NextSocketAction handle(H1CommandHandler handler) throws IOException {
        return handler.handleContent(this);
    }
}
