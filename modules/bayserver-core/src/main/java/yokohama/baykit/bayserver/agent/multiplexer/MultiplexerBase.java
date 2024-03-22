package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class MultiplexerBase implements Multiplexer {

    int channelCount;
    protected final GrandAgent agent;

    protected final Map<Object, RudderState> rudders = new HashMap<>();

    public MultiplexerBase(GrandAgent agt) {
        this.agent = agt;
    }

    @Override
    public String toString() {
        return agent.toString();
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////
    @Override
    public final void addRudderState(Rudder rd, RudderState st) {
        BayLog.trace("%s add rd=%s chState=%s", agent, rd, st);
        synchronized (rudders) {
            rudders.put(rd.key(), st);
        }
        channelCount++;

        st.access();
    }

    @Override
    public final RudderState getRudderState(Rudder rd) {
        return findRudderStateByKey(rd.key());
    }

    @Override
    public final Transporter getTransporter(Rudder rd) {
        return getRudderState(rd).transporter;
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        throw new Sink();
    }

    @Override
    public void reqRead(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        throw new Sink();
    }

    @Override
    public void reqEnd(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqClose(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        throw new Sink();
    }


    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    protected RudderState findRudderStateByKey(Object rdKey) {
        synchronized (rudders) {
            return rudders.get(rdKey);
        }
    }

    protected void removeRudderState(Rudder rd) {
        BayLog.trace("%s remove rd=%s", agent, rd);
        synchronized (rudders) {
            RudderState cm = rudders.remove(rd.key());
            //BayServer.debug(cm.tpt.ship() + " removed");
        }
        channelCount--;
    }

    protected final void closeRudder(RudderState chState) {
        BayLog.debug("%s closeRd %s state=%s closed=%b", agent, chState.rudder, chState, chState.closed);

        synchronized (this) {
            if(chState.closed)
                return;
            chState.closed = true;
        }

        try {
            chState.rudder.close();
        }
        catch(AsynchronousCloseException e) {
            BayLog.debug("Close error: %s", e);
        }
        catch(IOException e) {
            BayLog.error(e);
        }

        synchronized (chState.writeQueue) {
            // Clear queue
            for (WriteUnit wu : chState.writeQueue) {
                wu.done();
            }
            chState.writeQueue.clear();
        }

        if (chState.transporter != null)
            chState.transporter.onClosed(chState.rudder);

        removeRudderState(chState.rudder);
    }

    protected final void closeAll() {
        // Use copied ArrayList to avoid ConcurrentModificationException
        for (RudderState st : new ArrayList<>(rudders.values())) {
            closeRudder(st);
        }
    }

}
