package baykit.bayserver.docker.fcgi;

import baykit.bayserver.protocol.PacketFactory;

public class FcgPacketFactory extends PacketFactory<FcgPacket, FcgType> {

    @Override
    public FcgPacket createPacket(FcgType type) {
        return new FcgPacket(type);
    }
}
