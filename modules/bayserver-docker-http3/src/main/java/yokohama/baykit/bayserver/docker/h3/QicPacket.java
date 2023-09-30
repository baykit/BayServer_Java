package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.protocol.Packet;

import java.nio.ByteBuffer;

public class QicPacket extends Packet<QicType> {

    public static final int MAX_DATAGRAM_SIZE = 1350;

    public QicPacket() {
        super(QicType.Short, 0, MAX_DATAGRAM_SIZE);
    }

    public ByteBuffer asBuffer() {
        return ByteBuffer.wrap(buf, 0, dataLen());
    }

    @Override
    public String toString() {
        return "Quic packet[len=" + dataLen() + "]";
    }
}
