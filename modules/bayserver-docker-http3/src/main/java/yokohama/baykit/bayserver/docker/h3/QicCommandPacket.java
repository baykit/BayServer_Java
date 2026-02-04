package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.protocol.Packet;

/**
 * Dummy packet
 */
public class QicCommandPacket extends Packet {

    public QicCommandPacket(int type) {
        super(type, 0, 0);
    }

    @Override
    public String toString() {
        return "QicCmd packet[type=" + type + "]";
    }
}
