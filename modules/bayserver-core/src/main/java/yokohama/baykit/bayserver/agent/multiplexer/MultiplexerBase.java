package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        return agent.toString() + ":" + super.toString();
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////
    @Override
    public final void addRudderState(Rudder rd, RudderState st) {
        BayLog.trace("%s add rd=%s chState=%s", agent, rd, st);
        st.multiplexer = this;
        synchronized (rudders) {
            rudders.put(rd.key(), st);
        }
        channelCount++;

        st.access();
    }

    @Override
    public void removeRudderState(Rudder rd) {
        BayLog.trace("%s remove rd=%s", agent, rd);
        synchronized (rudders) {
            RudderState cm = rudders.remove(rd.key());
            //BayServer.debug(cm.tpt.ship() + " removed");
        }
        channelCount--;
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

        BayLog.trace("%s Flush buffer", agent);
    }

    @Override
    public final boolean isBusy() {
        return channelCount >= agent.maxInboundShips;
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    protected RudderState findRudderStateByKey(Object rdKey) {
        synchronized (rudders) {
            return rudders.get(rdKey);
        }
    }

    protected final void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        ArrayList<RudderState> closeList = new ArrayList<>();
        HashSet<RudderState> copied = null;
        synchronized (rudders) {
            copied = new HashSet<>(this.rudders.values());
        }

        long now = System.currentTimeMillis();

        for (RudderState st : copied) {
            if(st.transporter != null) {
                if (st.transporter.checkTimeout(st.rudder, (int) (now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                    closeList.add(st);
                }
            }
        }

        for (RudderState c : closeList) {
            reqClose(c.rudder);
        }
    }
    protected final void closeAll() {
        // Use copied ArrayList to avoid ConcurrentModificationException
        for (RudderState st : new ArrayList<>(rudders.values())) {
            if(st.rudder != agent.commandReceiver.rudder)
                closeRudder(st.rudder);
        }
    }


}
