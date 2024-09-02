package yokohama.baykit.bayserver.docker.http.h2.command;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.docker.http.h2.*;
import yokohama.baykit.bayserver.docker.http.h2.*;

import java.io.IOException;
import java.util.ArrayList;

/**
 * HTTP/2 Header payload format
 * 
 * +---------------+
 * |Pad Length? (8)|
 * +-+-------------+-----------------------------------------------+
 * |E|                 Stream Dependency? (31)                     |
 * +-+-------------+-----------------------------------------------+
 * |  Weight? (8)  |
 * +-+-------------+-----------------------------------------------+
 * |                   Header Block Fragment (*)                 ...
 * +---------------------------------------------------------------+
 * |                           Padding (*)                       ...
 * +---------------------------------------------------------------+
 */
public class CmdHeaders extends H2Command {
    
    public int padLength;
    public boolean excluded;
    public int streamDependency;
    public int weight;
    
    public ArrayList<HeaderBlock> headerBlocks = new ArrayList<>();
    
    public CmdHeaders(int streamId, H2Flags flags) {
        super(H2Type.Headers, streamId, flags);
    }

    public CmdHeaders(int streamId) {
        this(streamId, null);
    }



    @Override
    public void unpack(H2Packet pkt) {
        super.unpack(pkt);

        H2Packet.H2DataAccessor acc = pkt.newH2DataAccessor();

        if(pkt.flags.padded())
            padLength = acc.getByte();
        if(pkt.flags.priority()) {
            int val = acc.getInt();
            excluded = H2Packet.extractFlag(val) == 1;
            streamDependency = H2Packet.extractInt31(val);
            weight = acc.getByte();
        }
        readHeaderBlock(acc, pkt.dataLen());
        
    }


    @Override
    public void pack(H2Packet pkt) {
        H2Packet.H2DataAccessor acc = pkt.newH2DataAccessor();

        if(flags.padded()) {
            acc.putByte(padLength);
        }
        if(flags.priority()) {
            acc.putInt(H2Packet.makeStreamDependency32(excluded, streamDependency));
            acc.putByte(weight);
        }
        writeHeaderBlock(acc);
        super.pack(pkt);
    }

    @Override
    public NextSocketAction handle(H2CommandHandler handler) throws IOException {
        return handler.handleHeaders(this);
    }

    private void readHeaderBlock(H2Packet.H2DataAccessor acc, int len) {
        while(acc.pos < len) {
            HeaderBlock blk = HeaderBlock.unpack(acc);
            if(BayLog.isTraceMode())
                BayLog.trace("h2: header block read: " + blk);
            headerBlocks.add(blk);
        }
    }

    private void writeHeaderBlock(H2Packet.H2DataAccessor acc) {
        for(HeaderBlock blk : headerBlocks) {
            HeaderBlock.pack(blk, acc);
        }
    }

    public void addHeaderBlock(HeaderBlock blk) {
        headerBlocks.add(blk);
    }
}
