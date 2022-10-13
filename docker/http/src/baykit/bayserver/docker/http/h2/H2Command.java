package baykit.bayserver.docker.http.h2;

import baykit.bayserver.HttpException;
import baykit.bayserver.protocol.Command;

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

    public static H2Command create(H2Packet packet) throws IOException, HttpException {
        H2Command cmd;
        /*
        switch(packet.type) {
            case H2Packet.FRAME_TYPE_DATA:
                cmd = new Data();
                break;
                
            case H2Packet.FRAME_TYPE_HEADERS:
                cmd = new H2Headers();
                break;
                
            case H2Packet.FRAME_TYPE_PRIORITY:
            case H2Packet.FRAME_TYPE_RST_STREAM:
                cmd = null;
                break;
                
            case H2Packet.FRAME_TYPE_SETTINGS:
                cmd = new Settings();
                break;
                
            case H2Packet.FRAME_TYPE_WINDOW_UPDATE:
                cmd = new WindowUpdate();
                break;
                
            default:
                throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid frame type: " + packet.type);
        }
        cmd.unpack(packet);
        return cmd;

         */
        return null;
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
