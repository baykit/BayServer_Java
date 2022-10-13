package baykit.bayserver.docker.http.h2.command;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketPartAccessor;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.docker.http.h2.*;

import java.io.IOException;
import java.util.ArrayList;

/**
 * HTTP/2 Setting payload format
 * 
 * +-------------------------------+
 * |       Identifier (16)         |
 * +-------------------------------+-------------------------------+
 * |                        Value (32)                             |
 * +---------------------------------------------------------------+
 * 
 */
public class CmdSettings extends H2Command {

    public static class Item {
        public int id;
        public int value;

        public Item(int id, int value) {
            this.id = id;
            this.value = value;
        }
    }

    public static final int HEADER_TABLE_SIZE = 0x1;
    public static final int ENABLE_PUSH = 0x2;
    public static final int MAX_CONCURRENT_STREAMS = 0x3;
    public static final int INITIAL_WINDOW_SIZE = 0x4;
    public static final int MAX_FRAME_SIZE = 0x5;
    public static final int MAX_HEADER_LIST_SIZE = 0x6;

    public static final int INIT_HEADER_TABLE_SIZE = 4096;
    public static final int INIT_ENABLE_PUSH = 1;
    public static final int INIT_MAX_CONCURRENT_STREAMS = -1;
    public static final int INIT_INITIAL_WINDOW_SIZE = 65535;
    public static final int INIT_MAX_FRAME_SIZE = 16384;
    public static final int INIT_MAX_HEADER_LIST_SIZE = -1;
    
    public ArrayList<Item> items = new ArrayList<>();


    public CmdSettings(int streamId) {
        this(streamId, null);
    }

    public CmdSettings(int streamId, H2Flags flags) {
        super(H2Type.Settings, streamId, flags);
    }

    @Override
    public void unpack(H2Packet pkt) throws IOException {
        super.unpack(pkt);
        if(flags.ack()) {
            return;
        }

        PacketPartAccessor acc = pkt.newDataAccessor();
        int pos = 0;
        while(pos < pkt.dataLen()) {
            int id = acc.getShort();
            int value = acc.getInt();
            items.add(new Item(id, value));
            pos += 6;
        }
    }

    @Override
    public void pack(H2Packet pkt) throws IOException {
        
        if(flags.ack()) {
            // not pack payload
        }
        else {
            PacketPartAccessor acc = pkt.newDataAccessor();
            for (Item item : items) {
                acc.putShort(item.id);
                acc.putInt(item.value);
            }
        }

        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleSettings(this);
    }
}
