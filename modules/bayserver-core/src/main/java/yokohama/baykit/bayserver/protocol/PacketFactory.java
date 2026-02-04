package yokohama.baykit.bayserver.protocol;

import java.util.function.IntFunction;

public abstract class PacketFactory<P extends Packet> {
    public abstract P createPacket(int type);
}
