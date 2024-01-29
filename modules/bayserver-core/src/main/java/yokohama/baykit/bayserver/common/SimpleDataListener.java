package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class SimpleDataListener implements DataListener {

    final Ship ship;

    public SimpleDataListener(Ship ship) {
        this.ship = ship;
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    /////////////////////////////////////
    // Implements DataDispatcher
    /////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
        return ship.notifyHandshakeDone(protocol);
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        return ship.notifyConnect();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        return ship.notifyRead(buf);
    }

    @Override
    public NextSocketAction notifyEof() {
        return ship.notifyEof();
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        return ship.notifyProtocolError(e);
    }

    @Override
    public void notifyError(Throwable e) {
        BayLog.debug(e, "%s Error notified", this);
    }

    @Override
    public void notifyClose() {
        ship.notifyClose();
    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return ship.checkTimeout(durationSec);
    }
}
