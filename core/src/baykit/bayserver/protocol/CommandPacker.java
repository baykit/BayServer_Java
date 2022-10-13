package baykit.bayserver.protocol;


import baykit.bayserver.BayLog;
import baykit.bayserver.watercraft.Ship;
import baykit.bayserver.util.DataConsumeListener;
import baykit.bayserver.util.Reusable;

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

    public void post(Ship ship, C cmd) throws IOException {
        post(ship, cmd, null);
    }

    public void post(Ship sip, C cmd, DataConsumeListener listener) throws IOException {
        P pkt = pktStore.rent(cmd.type);

        try {
            cmd.pack(pkt);
            pktPacker.post(sip.postman, pkt, () -> {
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

    public void flush(Ship ship) {
        pktPacker.flush(ship.postman);
    }

    public void end(Ship ship) {
        pktPacker.end(ship.postman);
    }
}
