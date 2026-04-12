package yokohama.baykit.bayserver.protocol;


import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;

public class CommandPacker<C extends Command<C, P, H>, P extends Packet, H extends CommandHandler<C>>
        implements Reusable {

    protected final PacketPacker<P> pktPacker;
    protected final PacketStore<P> pktStore;

    public CommandPacker(PacketPacker<P> pktPacker, PacketStore<P> pktStore) {
        this.pktPacker = pktPacker;
        this.pktStore = pktStore;
    }

    /////////////////////////////////////////////////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
    }

    public void post(Ship sip, C cmd, boolean flush) throws IOException {
        post(sip, cmd, flush, null);
    }

    public void post(Ship sip, C cmd, boolean flush, DataConsumeListener listener) throws IOException {
        P pkt = pktStore.rent(cmd.type);

        try {
            cmd.pack(pkt);
            pktPacker.post(sip, pkt, flush, () -> {
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
