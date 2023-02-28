package baykit.bayserver.docker.http.h1;

import baykit.bayserver.BayLog;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.PacketUnpacker;
import baykit.bayserver.protocol.ProtocolException;
import baykit.bayserver.util.SimpleBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static baykit.bayserver.agent.NextSocketAction.Continue;
import static baykit.bayserver.agent.NextSocketAction.Suspend;


/**
 * Read HTTP header
 *
 *   HTTP/1.x has no packet format. So we make HTTP header and content pretend to be packet
 *
 *   From RFC2616
 *   generic-message : start-line
 *                     (message-header CRLF)*
 *                     CRLF
 *                     [message-body]
 *
 *
 */
public class H1PacketUnpacker extends PacketUnpacker<H1Packet> {

    enum State {
        ReadHeaders,
        ReadContent,
        End,
    }

    public static final int MAX_LINE_LEN = 8192;

    State state = State.ReadHeaders;

    final H1CommandUnPacker cmdUnpacker;
    final PacketStore<H1Packet, H1Type> pktStore;
    final SimpleBuffer tmpBuf;

    public H1PacketUnpacker(H1CommandUnPacker cmdUnpacker, PacketStore<H1Packet, H1Type> pktStore) {
        this.cmdUnpacker = Objects.requireNonNull(cmdUnpacker);
        this.pktStore = pktStore;
        this.tmpBuf = new SimpleBuffer();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        resetState();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements PacketUnpacker
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        if(state == State.End) {
            reset();
            throw new IllegalStateException();
        }

        int bufStart = buf.position();
        int lineLen = 0;
        boolean suspend = false;

        if(state == State.ReadHeaders) {
            loop:
            while (buf.hasRemaining()) {
                byte b = buf.get();
                tmpBuf.put(b);
                if (b == '\r')
                    continue;
                else if (b == '\n') {
                    if(lineLen == 0) {
                        H1Packet pkt = pktStore.rent(H1Type.Header);
                        pkt.newDataAccessor().putBytes(tmpBuf.bytes(), 0, tmpBuf.length());
                        NextSocketAction nextAct;
                        try {
                            nextAct = cmdUnpacker.packetReceived(pkt);
                        }
                        finally {
                            pktStore.Return(pkt);
                        }

                        switch(nextAct) {
                            case Continue:
                            case Suspend:
                                if(cmdUnpacker.reqFinished())
                                    changeState(State.End);
                                else
                                    changeState(State.ReadContent);
                                break loop;

                            case Close:
                                // Maybe error
                                resetState();
                                return nextAct;
                        }

                        suspend = (nextAct == Suspend);
                    }
                    lineLen = 0;
                }
                else {
                    lineLen++;
                }

                if(lineLen >= MAX_LINE_LEN) {
                    throw new ProtocolException("Http/1 Line is too long");
                }
            }
        }

        if(state == State.ReadContent) {
            while(buf.hasRemaining()) {
                H1Packet pkt = pktStore.rent(H1Type.Content);
                int len = buf.remaining();
                if(len > H1Packet.MAX_DATA_LEN)
                    len = H1Packet.MAX_DATA_LEN;

                BayLog.trace("H1 packetUnpack: remain=" + buf.remaining() + " consume=" + len);
                pkt.newDataAccessor().putBytes(buf.array(), buf.position(), len);
                buf.position(buf.position() + len);

                NextSocketAction nextAct;
                try {
                    nextAct = cmdUnpacker.packetReceived(pkt);
                }
                finally {
                    pktStore.Return(pkt);
                }

                switch(nextAct) {
                    case Continue:
                    case Write:
                        if(cmdUnpacker.reqFinished())
                            changeState(State.End);
                        break;
                    case Suspend:
                        suspend = true;
                        break;
                    case Close:
                        resetState();
                        return nextAct;
                }
            }
        }

        if(state == State.ReadHeaders)
            BayLog.debug("H1 not enough for packet (continue to read header): len=%d %s", tmpBuf.length(), new String(tmpBuf.bytes(), 0, tmpBuf.length()));

        if(state == State.End)
            resetState();

        if(suspend) {
            BayLog.debug("H1 read suspend");
            return Suspend;
        }
        else
            return Continue;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Other methods
    ////////////////////////////////////////////////////////////////////////////////


    private void changeState(State newState) {
        state = newState;
    }

    private void resetState() {
        changeState(State.ReadHeaders);
        tmpBuf.reset();
    }

    private boolean isAscii(byte c) {
        return c >= 32 && c <= 126;
    }
}
