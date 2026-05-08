package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.Command;

import java.io.IOException;

public abstract class H2Command extends Command<H2Command, H2Packet, H2CommandHandler> {

    public H2Flags flags;
    public int streamId;

    public H2Command(int type) {
        super(type);
    }

    public void init(int streamId, H2Flags flags) {
        this.streamId = streamId;
        if(flags == null)
            this.flags = new H2Flags();
        else
            this.flags = flags;
    }

    public void init(int streamId) {
        this.init(streamId, null);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        streamId = pkt.streamId;
        flags = pkt.flags;
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        pkt.streamId = streamId;
        pkt.flags = flags;
        pkt.packHeader();
    }
}
