package baykit.bayserver.protocol;

import baykit.bayserver.agent.transporter.Transporter;
import baykit.bayserver.util.DataConsumeListener;
import baykit.bayserver.util.Postman;
import baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PacketPacker<P extends Packet<?>> implements Reusable {

    @Override
    public void reset() {

    }

    public synchronized void post(Postman pm, P pkt, DataConsumeListener listener) throws IOException {
        if(listener == null)
            throw new NullPointerException();

        pm.post(ByteBuffer.wrap(pkt.buf, 0, pkt.bufLen), null, pkt, () -> listener.dataConsumed());
    }

    public void flush(Postman pm) {
        pm.flush();
    }

    public void end(Postman pm) {
        pm.postEnd();
    }
}
