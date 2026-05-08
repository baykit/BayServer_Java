package yokohama.baykit.bayserver.protocol;

public interface ProtocolHandlerFactory<C extends Command<C, P, ?>, P extends Packet> {

    ProtocolHandler<C, P> createProtocolHandler(PacketStore<P> pktStore, CommandStore<C> cmdStore);
}
