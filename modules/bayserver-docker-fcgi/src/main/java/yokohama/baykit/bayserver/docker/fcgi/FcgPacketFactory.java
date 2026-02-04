package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.PacketFactory;

public class FcgPacketFactory extends PacketFactory<FcgPacket> {

    @Override
    public FcgPacket createPacket(int type) {
        return new FcgPacket(type);
    }
}
