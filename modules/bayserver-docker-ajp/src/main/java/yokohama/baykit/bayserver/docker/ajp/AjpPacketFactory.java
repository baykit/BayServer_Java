package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.PacketFactory;

public class AjpPacketFactory extends PacketFactory<AjpPacket, AjpType> {

    @Override
    public AjpPacket createPacket(AjpType type) {
        return new AjpPacket(type);
    }

}
