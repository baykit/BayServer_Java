package baykit.bayserver.docker.http.h2;

import baykit.bayserver.protocol.PacketFactory;

public class H2PacketFactory extends PacketFactory<H2Packet, H2Type> {

    @Override
    public H2Packet createPacket(H2Type type) {
        return new H2Packet(type);
    }
}
