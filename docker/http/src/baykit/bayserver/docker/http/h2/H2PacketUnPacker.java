package baykit.bayserver.docker.http.h2;

import baykit.bayserver.BayLog;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.protocol.PacketUnpacker;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.util.SimpleBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

import static baykit.bayserver.agent.NextSocketAction.Continue;
import static baykit.bayserver.agent.NextSocketAction.Suspend;


/**

 *
 */
public class H2PacketUnPacker extends PacketUnpacker<H2Packet> {

    /** refers tmpBuf */
    class FrameHeaderItem {
        int start;
        int len;
        int pos; // relative reading position

        FrameHeaderItem(int start, int len) {
            this.start = start;
            this.len = len;
            pos = 0;
        }
        
        int get(int index) {
            return tmpBuf.bytes()[start + index] & 0xFF;
        }
    }

    public enum State {
        // HTTP/2.0
        ReadLength,
        ReadType,
        ReadFlags,
        ReadStreamIdentifier,
        ReadFlamePayload,
        End
    }

    private static final int FRAME_LEN_LENGTH = 3;
    private static final int FRAME_LEN_TYPE = 1;
    private static final int FRAME_LEN_FLAGS = 1;
    private static final int FRAME_LEN_STREAM_IDENTIFIER = 4;

    public static final int FLAGS_END_STREAM = 0x1;
    public static final int FLAGS_END_HEADERS = 0x4;
    public static final int FLAGS_PADDED = 0x8;
    public static final int FLAGS_PRIORITY = 0x20;

    public static final byte[] CONNECTION_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
    
    
    State state = State.ReadLength;
    SimpleBuffer tmpBuf = new SimpleBuffer();
    FrameHeaderItem item;
    boolean prefaceRead;
    H2Type type;
    int payloadLen;
    int flags;
    int streamId;
    
    final H2CommandUnPacker cmdUnpacker;
    final PacketStore<H2Packet, H2Type> pktStore;
    final boolean serverMode;

    int contLen;
    int readBytes;
    
    public H2PacketUnPacker(H2CommandUnPacker cmdUnpacker, PacketStore<H2Packet, H2Type> pktStore, boolean serverMode) {
        this.cmdUnpacker = cmdUnpacker;
        this.pktStore = pktStore;
        this.serverMode = serverMode;
        reset();
    }


    @Override
    public void reset() {
        resetState();
        prefaceRead = false;
    }

    public void resetState() {
        changeState(State.ReadLength);
        item = new FrameHeaderItem(0, FRAME_LEN_LENGTH);
        contLen = 0;
        readBytes = 0;
        tmpBuf.reset();
        type = null;
        flags = 0;
        streamId = 0;
        payloadLen = 0;
    }


    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        boolean suspend = false;

        if(serverMode && !prefaceRead) {
            int len = CONNECTION_PREFACE.length - tmpBuf.length();
            if(len > buf.remaining())
                len = buf.remaining();
            tmpBuf.put(buf, len);
            if(tmpBuf.length() == CONNECTION_PREFACE.length) {
                for(int i = 0; i < tmpBuf.length(); i++) {
                    if(CONNECTION_PREFACE[i] != tmpBuf.bytes()[i])
                        throw new ProtocolException("Invalid connection preface: " + new String(tmpBuf.bytes(), 0, tmpBuf.length()));
                }
                H2Packet pkt = pktStore.rent(H2Type.Preface);
                pkt.newDataAccessor().putBytes(tmpBuf.bytes(), 0, tmpBuf.length());
                NextSocketAction nstat = cmdUnpacker.packetReceived(pkt);
                pktStore.Return(pkt);
                if(nstat != Continue)
                    return nstat;

                BayLog.debug("Connection preface OK");
                prefaceRead = true;
                tmpBuf.reset();
            }
        }
        
        while (state != State.End && buf.hasRemaining()) {
            switch (state) {
                case ReadLength:
                    if(readHeaderItem(buf)) {
                        payloadLen = ((item.get(0) & 0xFF) << 16 | (item.get(1) & 0xFF) << 8 | (item.get(2) & 0xFF));
                        item = new FrameHeaderItem(tmpBuf.length(), FRAME_LEN_TYPE);
                        changeState(State.ReadType);
                    }
                    break;

                case ReadType:
                    if(readHeaderItem(buf)) {
                        type = H2Type.getType(item.get(0));
                        item = new FrameHeaderItem(tmpBuf.length(), FRAME_LEN_FLAGS);
                        changeState(State.ReadFlags);
                    }
                    break;

                case ReadFlags:
                    if(readHeaderItem(buf)) {
                        flags = item.get(0);
                        item = new FrameHeaderItem(tmpBuf.length(), FRAME_LEN_STREAM_IDENTIFIER);
                        changeState(State.ReadStreamIdentifier);
                    }
                    break;

                case ReadStreamIdentifier:
                    if(readHeaderItem(buf)) {
                        streamId = ((item.get(0) & 0x7F) << 24) |
                                (item.get(1) << 16) |
                                (item.get(2) << 8) |
                                item.get(3);
                        item = new FrameHeaderItem(tmpBuf.length(), payloadLen);
                        changeState(State.ReadFlamePayload);
                    }
                    break;

                case ReadFlamePayload:
                    if(readHeaderItem(buf)) {
                        changeState(State.End);
                    }
                    break;

                default:
                    throw new IllegalStateException();
            }


            if (state == State.End) {
                H2Packet pkt = pktStore.rent(type);
                pkt.streamId = streamId;
                pkt.flags = new H2Flags(flags);
                pkt.newHeaderAccessor().putBytes(tmpBuf.bytes(), 0, H2Packet.FRAME_HEADER_LEN);
                pkt.newDataAccessor().putBytes(tmpBuf.bytes(), H2Packet.FRAME_HEADER_LEN, tmpBuf.length() - H2Packet.FRAME_HEADER_LEN);
                NextSocketAction nxtAct;
                try {
                    nxtAct = cmdUnpacker.packetReceived(pkt);
                    //BayServer.debug("H2 NextAction=" + nxtAct + " sz=" + tmpBuf.length() + " remain=" + buf.hasRemaining());
                }
                finally {
                    pktStore.Return(pkt);
                    resetState();
                }
                if(nxtAct == Suspend) {
                    suspend = true;
                }
                else if(nxtAct != Continue)
                    return nxtAct;
            }
        }

        if(suspend)
            return Suspend;
        else
            return Continue;
    }


    /////////////////////////////////////////////////////////////////////////
    // private methods
    /////////////////////////////////////////////////////////////////////////

    private boolean readHeaderItem(ByteBuffer buf) {
        int len = item.len - item.pos;
        if(buf.remaining() < len)
            len = buf.remaining();
        tmpBuf.put(buf, len);
        item.pos += len;

        return item.pos == item.len;
    }

    private void changeState(State newState) {
        state = newState;
    }
}
