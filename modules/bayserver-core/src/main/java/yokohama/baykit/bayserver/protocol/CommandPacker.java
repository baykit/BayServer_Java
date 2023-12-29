package yokohama.baykit.bayserver.protocol;


import yokohama.baykit.bayserver.common.Postman;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;

public class CommandPacker<C extends Command<C, P, T, H>, P extends Packet<T>, T, H extends CommandHandler<C>>
        implements Reusable {

    protected final PacketPacker<P> pktPacker;
    protected final PacketStore<P, T> pktStore;

    public CommandPacker(PacketPacker<P> pktPacker, PacketStore<P, T> pktStore) {
        this.pktPacker = pktPacker;
        this.pktStore = pktStore;
    }

    /////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
    }

    public void post(Postman pm, C cmd) throws IOException {
        post(pm, cmd, null);
    }

    public void post(Postman pm, C cmd, DataConsumeListener listener) throws IOException {
        P pkt = pktStore.rent(cmd.type);

        try {
            cmd.pack(pkt);
            pktPacker.post(pm, pkt, () -> {
                pktStore.Return(pkt);
                if (listener != null)
                    listener.dataConsumed();
            });
        }
        catch(IOException e) {
            pktStore.Return(pkt);
            throw e;
        }
    }
}
