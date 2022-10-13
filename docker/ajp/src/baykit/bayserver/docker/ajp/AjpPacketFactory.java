package baykit.bayserver.docker.ajp;

import baykit.bayserver.protocol.PacketFactory;

public class AjpPacketFactory extends PacketFactory<AjpPacket, AjpType> {

    @Override
    public AjpPacket createPacket(AjpType type) {
        return new AjpPacket(type);
    }

}
