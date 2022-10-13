package baykit.bayserver.protocol;

public interface ProtocolHandlerFactory<C extends Command<C, P, T, ?>, P extends Packet<T>, T> {

    ProtocolHandler<C, P, T> createProtocolHandler(PacketStore<P, T> pktStore);
}
