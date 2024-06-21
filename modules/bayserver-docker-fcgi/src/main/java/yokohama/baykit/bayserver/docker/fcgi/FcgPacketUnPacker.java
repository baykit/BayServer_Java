package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.PacketUnpacker;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.SimpleBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Packet unmarshall logic for FCGI
 */
public class FcgPacketUnPacker extends PacketUnpacker<FcgPacket> {

    SimpleBuffer headerBuf = new SimpleBuffer();
    SimpleBuffer dataBuf = new SimpleBuffer();

    int version;
    int typeNo;
    FcgType type;
    int reqId;
    int length;
    int padding;
    int paddingReadBytes;

    enum State {
        ReadPreamble,   // state for reading first 8 bytes (from version to reserved)
        ReadContent,  // state for reading content data
        ReadPadding,  // state for reading padding data
        End
    }
    
    State state;

    final FcgCommandUnPacker cmdUnpacker;
    final PacketStore<FcgPacket, FcgType> pktStore;
    int contLen;
    int readBytes;
    
    public FcgPacketUnPacker(FcgCommandUnPacker cmdUnpacker, PacketStore<FcgPacket, FcgType> pktStore) {
        this.cmdUnpacker = cmdUnpacker;
        this.pktStore = pktStore;
        reset();
    }

    public String toString() {
        return "FcgPacketUnPacker#" + hashCode() + " (" + Thread.currentThread().getName() + ")";
    }


    @Override
    public void reset() {
        state = State.ReadPreamble;
        version = 0;
        type = null;
        reqId = 0;
        length = 0;
        padding = 0;
        paddingReadBytes = 0;
        contLen = 0;
        readBytes = 0;
        headerBuf.reset();
        dataBuf.reset();
    }

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        boolean nextSuspend = false;
        boolean nextWrite = false;

        while(buf.hasRemaining()) {
            while (state != State.End && buf.hasRemaining()) {
                switch (state) {
                    case ReadPreamble: {
                        // preamble read mode
                        int len = FcgPacket.PREAMBLE_SIZE - headerBuf.length();
                        if (buf.remaining() < len)
                            len = buf.remaining();
                        headerBuf.put(buf, len);
                        if (headerBuf.length() == FcgPacket.PREAMBLE_SIZE) {
                            headerReadDone();
                            if (type == null) {
                                throw new ProtocolException("Invalid FCGI Type: " + typeNo);
                            }
                            if (length == 0) {
                                if (padding == 0)
                                    changeState(State.End);
                                else
                                    changeState(State.ReadPadding);
                            } else {
                                changeState(State.ReadContent);
                            }
                        }
                        break;
                    }
                    case ReadContent: {
                        // content read mode
                        int len = length - dataBuf.length();
                        if (len > buf.remaining()) {
                            len = buf.remaining();
                        }
                        if (len > 0) {
                            dataBuf.put(buf, len);
                            if (dataBuf.length() == length) {
                                if (padding == 0)
                                    changeState(State.End);
                                else
                                    changeState(State.ReadPadding);
                            }
                        }
                        break;
                    }
                    case ReadPadding: {
                        // padding read mode
                        int len = padding - paddingReadBytes;
                        if (len > buf.remaining()) {
                            len = buf.remaining();
                        }
                        buf.position(buf.position() + len);
                        if (len > 0) {
                            paddingReadBytes += len;
                            if (paddingReadBytes == padding) {
                                changeState(State.End);
                            }
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }

            if (state == State.End) {
                FcgPacket pkt = pktStore.rent(type);
                pkt.reqId = reqId;
                pkt.newHeaderAccessor().putBytes(headerBuf.bytes(), 0, headerBuf.length());
                pkt.newDataAccessor().putBytes(dataBuf.bytes(), 0, dataBuf.length());
                NextSocketAction state;
                try {
                    state = cmdUnpacker.packetReceived(pkt);
                }
                finally {
                    pktStore.Return(pkt);
                }
                reset();

                switch(state) {
                    case Suspend:
                        nextSuspend = true;
                        break;

                    case Continue:
                        break;

                    case Write:
                        nextWrite = true;
                        break;

                    case Close:
                        return state;

                    default:
                        throw new Sink();
                }
            }
        }

        if(nextWrite)
            return NextSocketAction.Write;
        else if(nextSuspend)
            return NextSocketAction.Suspend;
        else
            return NextSocketAction.Continue;
    }

    /////////////////////////////////////////////////////////////////////////
    // private methods
    /////////////////////////////////////////////////////////////////////////
    private void changeState(State newState) {
        state = newState;
    }

    private void headerReadDone() {
        byte[] pre = headerBuf.bytes();
        version = byteToInt(pre[0]);
        typeNo = byteToInt(pre[1]);
        type = FcgType.getType(typeNo);
        reqId = bytesToInt(pre[2], pre[3]);
        length = bytesToInt(pre[4], pre[5]);
        padding = byteToInt(pre[6]);
        int reserved = byteToInt(pre[7]);
        BayLog.debug("%s fcg Read packet header: version=%s type=%s reqId=%d length=%d padding=%d",
                        this, version, type, reqId, length, padding);
    }

    private int byteToInt(byte b) {
        return b & 0xff;
    }

    private int bytesToInt(byte b1, byte b2) {
        return byteToInt(b1) << 8 | byteToInt(b2);
    }

}
