package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.Command;

import java.io.IOException;

public abstract class H2Command extends Command<H2Command, H2Packet, H2Type, H2CommandHandler> {

    public H2Flags flags;
    public int streamId;

    public H2Command(H2Type type, int streamId) {
        this(type, streamId, null);
    }

    public H2Command(H2Type type, int streamId, H2Flags flags) {
        super(type);
        this.streamId = streamId;
        if(flags == null)
            this.flags = new H2Flags();
        else
            this.flags = flags;
    }

    @Override
    public void unpack(H2Packet pkt) {
        streamId = pkt.streamId;
        flags = pkt.flags;
    }

    @Override
    public void pack(H2Packet pkt) {
        pkt.streamId = streamId;
        pkt.flags = flags;
        pkt.packHeader();
    }
}
