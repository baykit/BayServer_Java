package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.PlainTransporter;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Postman;
import yokohama.baykit.bayserver.util.Valve;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public abstract class ReadOnlyShip extends Ship {

    public InputStream input;

    protected void init(InputStream input, GrandAgent agt, Valve vlv) {
        super.init(null, agt, new Postman() {
            @Override
            public void post(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
                throw new Sink();
            }

            @Override
            public void flush() {
                throw new Sink();
            }

            @Override
            public void postEnd() {
                throw new Sink();
            }

            @Override
            public boolean isZombie() {
                throw new Sink();
            }

            @Override
            public void abort() {
                throw new Sink();
            }

            @Override
            public void reset() {
                throw new Sink();
            }

            @Override
            public void openValve() {
                vlv.openValve();
            }
        });
        this.input = input;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////
    public void reset() {
        super.reset();
        this.input = null;
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract NextSocketAction bytesReceived(ByteBuffer buf) throws IOException;
    public abstract void notifyError(Throwable e);
    public abstract NextSocketAction notifyEof();
    public abstract void notifyClose();
    public abstract boolean checkTimeout(int durationSec);


}
