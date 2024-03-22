package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PacketPacker<P extends Packet<?>> implements Reusable {

    @Override
    public void reset() {

    }

    public synchronized void post(Ship sip, P pkt, DataConsumeListener listener) throws IOException {
        if(listener == null)
            throw new NullPointerException();

        sip.transporter.reqWrite(
                sip.rudder,
                ByteBuffer.wrap(pkt.buf, 0, pkt.bufLen),
                null,
                pkt, () -> listener.dataConsumed());
    }
}
