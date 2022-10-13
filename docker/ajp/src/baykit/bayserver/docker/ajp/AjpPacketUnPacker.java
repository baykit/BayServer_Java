package baykit.bayserver.docker.ajp;

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
 * AJP Protocol
 * https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 *
 */
public class AjpPacketUnPacker extends PacketUnpacker<AjpPacket> {

    SimpleBuffer preambleBuf = new SimpleBuffer();
    SimpleBuffer bodyBuf = new SimpleBuffer();

    enum State {
        ReadPreamble,
        ReadBody,
        End
    }
    
    State state = State.ReadPreamble;

    final PacketStore<AjpPacket, AjpType> pktStore;
    final AjpCommandUnPacker cmdUnpacker;
    int bodyLen;
    int readBytes;
    AjpType type;
    boolean toServer;
    boolean needData;

    public AjpPacketUnPacker(PacketStore<AjpPacket, AjpType> pktStore, AjpCommandUnPacker cmdUnpacker) {
        this.pktStore = pktStore;
        this.cmdUnpacker = cmdUnpacker;
    }

    @Override
    public void reset() {
        state = State.ReadPreamble;
        bodyLen = 0;
        readBytes = 0;
        needData = false;
        preambleBuf.reset();
        bodyBuf.reset();
    }

    @Override
    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        boolean suspend = false;

        while (buf.hasRemaining()) {
            if (state == State.ReadPreamble) {
                int len = AjpPacket.PREAMBLE_SIZE - preambleBuf.length();
                if (buf.remaining() < len)
                    len = buf.remaining();
                preambleBuf.put(buf, len);
                if (preambleBuf.length() == AjpPacket.PREAMBLE_SIZE) {
                    preambleRead();
                    changeState(State.ReadBody);
                }
            }

            if (state == State.ReadBody) {
                int len = bodyLen - bodyBuf.length();
                if (len > buf.remaining()) {
                    len = buf.remaining();
                }
                bodyBuf.put(buf, len);
                if (bodyBuf.length() == bodyLen) {
                    bodyRead();
                    changeState(State.End);
                }
            }

            if (state == State.End) {
                //BayLog.trace("ajp: parse end: preamblelen=" + preambleBuf.length() + " bodyLen=" + bodyBuf.length() + " type=" + type);
                AjpPacket pkt = pktStore.rent(type);
                pkt.toServer = toServer;
                pkt.newAjpHeaderAccessor().putBytes(preambleBuf.bytes(), 0, preambleBuf.length());
                pkt.newAjpDataAccessor().putBytes(bodyBuf.bytes(), 0, bodyBuf.length());
                NextSocketAction nextSocketAction;
                try {
                    nextSocketAction = cmdUnpacker.packetReceived(pkt);
                }
                finally {
                    pktStore.Return(pkt);
                }
                reset();
                needData = cmdUnpacker.needData();

                if(nextSocketAction == Suspend) {
                    suspend = true;
                }
                else if(nextSocketAction != Continue)
                    return nextSocketAction;
            }
        }

        BayLog.debug("ajp next read");
        if(suspend)
            return Suspend;
        else
            return Continue;
    }

    /////////////////////////////////////////////////////////////////////////
    // private methods
    /////////////////////////////////////////////////////////////////////////

    private void changeState(State newState) {
        state = newState;
    }

    private void preambleRead() throws IOException {
        byte[] data = preambleBuf.bytes();

        if (data[0] == 0x12 && data[1] == 0x34)
            toServer = true;
        else if (data[0] == 'A' && data[1] == 'B')
            toServer = false;
        else
            throw new ProtocolException("Must be start with 0x1234 or 'AB'");

        bodyLen = ((data[2] << 8) | (data[3] & 0xff)) & 0xffff;
        BayLog.trace("ajp: read packet preamble: bodyLen=" + bodyLen);
    }

    private void bodyRead() {
        if(needData)
            type = AjpType.Data;
        else
            type = AjpType.getType(bodyBuf.bytes()[0] & 0xff);
    }

}
