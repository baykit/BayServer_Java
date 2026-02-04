package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.PacketFactory;

public class AjpPacketFactory extends PacketFactory<AjpPacket> {

    @Override
    public AjpPacket createPacket(int type) {
        return new AjpPacket(type);
    }

}
