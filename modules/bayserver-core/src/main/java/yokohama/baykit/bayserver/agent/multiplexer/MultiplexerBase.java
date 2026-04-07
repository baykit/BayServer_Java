package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.Transporter;
import yokohama.baykit.bayserver.common.WriteUnit;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.util.*;

public abstract class MultiplexerBase implements Multiplexer {

    int channelCount;
    protected final GrandAgent agent;

    protected final HashSet<ChannelRudder> rudders = new HashSet<>();

    public MultiplexerBase(GrandAgent agt) {
        this.agent = agt;
    }

    @Override
    public String toString() {
        return agent.toString() + ":" + super.toString();
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////
    @Override
    public final void addRudderState(Rudder rd, RudderState st) {
        BayLog.trace("%s add rd=%s chState=%s", agent, rd, st);
        st.multiplexer = this;
        ((ChannelRudder)rd).state = st;
        channelCount++;

        st.access();
    }

    @Override
    public void removeRudderState(Rudder rd) {
        BayLog.trace("%s remove rd=%s", agent, rd);
        ((ChannelRudder)rd).state = null;
        channelCount--;
    }

    @Override
    public final RudderState getRudderState(Rudder rd) {
        return (RudderState) ((ChannelRudder)rd).state;

    }

    @Override
    public final Transporter getTransporter(Rudder rd) {
        return getRudderState(rd).transporter;
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
    public boolean consumeOldestUnit(RudderState st) {
        WriteUnit u;
        synchronized (st.writeQueue) {
            if(st.writeQueue.isEmpty())
                return false;
            u = st.writeQueue.remove(0);
        }
        u.done();
        return true;
    }

    @Override
    public final void closeRudder(Rudder rd) {
        BayLog.debug("%s closeRd %s", agent, rd);

        try {
            BayLog.trace("%s OS Close", agent);
            rd.close();
        }
        catch(AsynchronousCloseException e) {
            BayLog.debug("Close error: %s", e);
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    @Override
    public boolean useUringAPI() {
        return false;
    }

    @Override
    public final boolean isBusy() {
        return channelCount >= agent.maxInboundShips;
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    protected final void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        long now = RoughTime.currentTimeMillis();

        for (Iterator<ChannelRudder> it = rudders.iterator(); it.hasNext(); ) {
            ChannelRudder rd = it.next();
            if(rd.closed())
                it.remove();

            RudderState st = (RudderState)rd.state;
            if(st.transporter != null) {
                if (st.transporter.checkTimeout(rd, (int) (now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: rd=%s", agent, rd);
                    reqClose(rd);
                    it.remove();
                }
            }
        }
    }

    protected final void closeAll() {
        // Use copied ArrayList to avoid ConcurrentModificationException
        for (Iterator<ChannelRudder> it = rudders.iterator(); it.hasNext(); ) {
            ChannelRudder rd = it.next();
            if(rd != agent.commandReceiver.rudder) {
                closeRudder(rd);
                it.remove();
            }
        }
    }


}
