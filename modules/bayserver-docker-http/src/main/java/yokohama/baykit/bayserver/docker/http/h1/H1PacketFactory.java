package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.protocol.PacketFactory;

public class H1PacketFactory extends PacketFactory<H1Packet> {

    @Override
    public H1Packet createPacket(int type) {
        return new H1Packet(type);
    }
}
