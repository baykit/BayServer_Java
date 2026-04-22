package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PacketPacker<P extends Packet> implements Reusable {

    @Override
    public void reset() {

    }

    public synchronized boolean post(Ship sip, P pkt, boolean flush, DataConsumeListener listener) throws IOException {
        if(listener == null)
            throw new NullPointerException();

        // Pass the listener through instead of wrapping it in a pass-through
        // lambda; the wrapper was a hot allocation (one per post call) with
        // no added behaviour.
        return sip.transporter.reqWrite(
                sip.rudder,
                ByteBuffer.wrap(pkt.buf, 0, pkt.bufLen),
                null,
                pkt, flush, listener);
    }
}
