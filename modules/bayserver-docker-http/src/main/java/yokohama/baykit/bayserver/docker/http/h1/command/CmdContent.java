package yokohama.baykit.bayserver.docker.http.h1.command;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.PacketPartAccessor;
import yokohama.baykit.bayserver.docker.http.h1.H1Command;
import yokohama.baykit.bayserver.docker.http.h1.H1CommandHandler;
import yokohama.baykit.bayserver.docker.http.h1.H1Packet;
import yokohama.baykit.bayserver.docker.http.h1.H1Type;

import java.io.IOException;

public class CmdContent extends H1Command {
    public byte[] buffer;
    public int start;
    public int len;
    
    public CmdContent() {
        super(H1Type.Content);
    }

    public void init(byte[] buf, int start, int len) {
        this.buffer = buf;
        this.start = start;
        this.len = len;
    }


    ///////////////////////////////////////////////
    // Implements Reusable
    ///////////////////////////////////////////////

    @Override
    public void reset() {

    }


    ///////////////////////////////////////////////
    // Implements Command
    ///////////////////////////////////////////////

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
