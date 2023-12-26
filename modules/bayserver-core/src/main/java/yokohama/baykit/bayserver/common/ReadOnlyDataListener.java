package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class ReadOnlyDataListener implements DataListener {

    ReadOnlyShip ship;

    public ReadOnlyDataListener(ReadOnlyShip ship) {
        this.ship = ship;
    }


    @Override
    public String toString() {
        return ship.toString();
    }

    ////////////////////////////////////////////////////////////////////
    // Implements DataListener
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        return ship.bytesReceived(buf);
    }

    @Override
    public NextSocketAction notifyEof() {
        BayLog.debug("%s Notify EOF", this);
        return ship.notifyEof();
    }

    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
        throw new Sink();
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

    @Override
    public void notifyClose() {
        BayLog.debug("%s Channel closed", this);
        ship.notifyClose();
    }

    @Override
    public final boolean checkTimeout(int durationSec) {
        BayLog.debug("%s Check timeout: dur=%d", this, durationSec);
        return ship.checkTimeout(durationSec);
    }
}
