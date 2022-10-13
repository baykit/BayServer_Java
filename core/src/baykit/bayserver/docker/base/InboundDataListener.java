package baykit.bayserver.docker.base;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.agent.transporter.DataListener;
import baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static baykit.bayserver.agent.NextSocketAction.Close;

public class InboundDataListener implements DataListener {

    final InboundShip ship;

    public InboundDataListener(InboundShip ship) {
        this.ship = ship;
    }

    @Override
    public String toString() {
        return ship.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements DataListener
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction notifyHandshakeDone(String pcl) {
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction notifyConnect() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        return ship.protocolHandler.bytesReceived(buf);
    }

    @Override
    public NextSocketAction notifyEof() throws IOException {
        BayLog.debug("%s EOF detected", this);
        return Close;
    }

    @Override
    public boolean notifyProtocolError(ProtocolException e) throws IOException {
        if(BayLog.isDebugMode()) {
            BayLog.error(e);
        }
        return ((InboundHandler)ship.protocolHandler).sendReqProtocolError(e);
    }

    @Override
    public final synchronized void notifyClose() {
        BayLog.debug("%s notifyClose", this);

        ship.abortTours();

        if(!ship.activeTours.isEmpty()) {
            // cannot close because there are some running tours
            BayLog.debug(this + " cannot end ship because there are some running tours (ignore)");
            ship.needEnd = true;
        }
        else {
            ship.endShip();
        }
    }


    @Override
    public boolean checkTimeout(int durationSec) {
        boolean timeout;
        if(ship.socketTimeoutSec <= 0)
            timeout = false;
        else if(ship.keeping)
            timeout = durationSec >= BayServer.harbor.keepTimeoutSec();
        else
            timeout = durationSec >= ship.socketTimeoutSec;

        BayLog.debug("%s Check timeout: dur=%d, timeout=%b, keeping=%b limit=%d keeplim=%d",
                this, durationSec, timeout, ship.keeping, ship.socketTimeoutSec, BayServer.harbor.keepTimeoutSec());
        return timeout;
    }
}
