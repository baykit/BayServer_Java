package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class TaxiMultiplexer extends MultiplexerBase {

    public TaxiMultiplexer(GrandAgent agt) {
        super(agt);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqAccept(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        throw new Sink();
    }

    @Override
    public void reqRead(Rudder rd) {
        BayLog.debug("%s TaxiMpx reqRead rd=%s", this, rd);
        RudderState st = getRudderState(rd);

        boolean needRead = false;
        synchronized (st.reading) {
            if (!st.reading[0]) {
                needRead = true;
                st.reading[0] = true;
            }
        }

        BayLog.debug("%s needRead=%s", agent, needRead);
        if(needRead) {
            nextRun(st);
        }
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        BayLog.debug("%s TaxiMpx reqWrite rd=%s buf=%s", this, rd, buf);
        RudderState st = getRudderState(rd);
        if(st == null || st.closed) {
            throw new IOException(this + " Invalid rudder: " + rd);
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (st.writeQueue) {
            st.writeQueue.add(unt);
        }

        boolean needWrite = false;
        synchronized (st.writing) {
            if (!st.writing[0]) {
                needWrite = true;
                st.writing[0] = true;
            }
        }

        if(needWrite) {
            nextRun(st);
        }

        st.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqClose(Rudder rd) {
        BayLog.debug("%s TaxiMpx reqClose rd=%s", this, rd);
        closeRudder(rd);
        RudderState st = getRudderState(rd);
        agent.sendClosedLetter(st, true);
    }

    @Override
    public void cancelRead(RudderState st) {

    }

    @Override
    public void cancelWrite(RudderState st) {

    }

    @Override
    public void nextAccept(RudderState state) {
        throw new Sink();
    }

    @Override
    public void nextRead(RudderState st) {
        nextRun(st);
    }

    @Override
    public void nextWrite(RudderState st) {
        nextRun(st);
    }

    @Override
    public void shutdown() {
        closeAll();
    }

    @Override
    public boolean isNonBlocking() {
        return false;
    }

    @Override
    public boolean useAsyncAPI() {
        return false;
    }

    @Override
    public void onBusy() {
        throw new Sink();
    }

    @Override
    public void onFree() {
        throw new Sink();
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    /*
    protected void onTimer(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.access();

        int durationSec = (int)(System.currentTimeMillis() - st.lastAccessTime) / 1000;
        if (st.transporter.checkTimeout(st.rudder, durationSec))
            closeRudder(st);
    }
    */

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private void nextRun(RudderState st) {
        BayLog.debug("%s POST NEXT RUN: %s", this, st);
        if(st == null)
            throw new NullPointerException();
        TaxiRunner.post(agent.agentId, new Taxi() {
            @Override
            protected void depart() {
                if(st.rudder instanceof ReadableByteChannelRudder)
                    nextRead(st.rudder);
                else
                    nextWrite(st.rudder);
            }

            @Override
            protected void onTimer() {
                if(st.transporter != null)
                    st.transporter.checkTimeout(st.rudder, -1);
            }
        });
    }

    private void nextRead(Rudder rd) {
        RudderState st = getRudderState(rd);

        try {
            int len = ((ReadableByteChannel)ChannelRudder.getChannel(rd)).read(st.readBuf);

            if (len <= 0) {
                st.readBuf.limit(st.readBuf.position());
            }
            else {
                st.readBuf.flip();
            }
            agent.sendReadLetter(st, len, null, true);
        }
        catch(Throwable e) {
            agent.sendErrorLetter(st, e, true);
        }
    }

    private void nextWrite(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.access();

        try {
            if(st.writeQueue.isEmpty())
                throw new IllegalStateException(this + " Write queue is empty!");
            WriteUnit u = st.writeQueue.get(0);
            int len;
            if(u.buf.limit() == 0) {
                len = 0;
            }
            else {
                len = ((WritableByteChannel) ChannelRudder.getChannel(rd)).write(u.buf);
            }
            agent.sendWroteLetter(st, len,true);
        }
        catch(Throwable e) {
            agent.sendErrorLetter(st, e, true);
        }
    }
}
