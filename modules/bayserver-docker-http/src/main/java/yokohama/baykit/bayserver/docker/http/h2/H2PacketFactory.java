package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.PacketFactory;

public class H2PacketFactory extends PacketFactory<H2Packet> {

    @Override
    public H2Packet createPacket(int type) {
        return new H2Packet(type);
    }
}
