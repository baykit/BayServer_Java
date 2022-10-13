package baykit.bayserver.protocol;

import java.util.function.Function;

public abstract class PacketFactory<P extends Packet<T>, T> implements Function<T, P> {
    public abstract P createPacket(T type);

    @Override
    public final P apply(T type) {
        return apply(type);
    }
}
