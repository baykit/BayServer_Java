package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.AsynchronousSocketChannelRudder;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.TimeUnit;

public class PigeonMultiplexer extends JobMultiplexerBase {

    public PigeonMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent, anchorable);
        if(!anchorable) {
            BayLog.debug("Unanchorable mode is not supported");
        }
    }

    public String toString() {
        return "PgnMpx[" + agent + "]";
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqAccept(Rudder rd) {
        BayLog.debug("%s reqAccept rd=%s aborted=%b", agent, rd, agent.aborted);
        if (agent.aborted) {
            return;
        }
        AsynchronousServerSocketChannel sch = (AsynchronousServerSocketChannel) ChannelRudder.getChannel(rd);
        RudderState st = findRudderStateByKey(sch);

        try {
            AsynchronousSocketChannel ch = null;
            sch.accept(
                    rd,
                    new CompletionHandler<AsynchronousSocketChannel, Rudder>() {
                        @Override
                        public void completed(AsynchronousSocketChannel clientCh, Rudder serverRd) {
                            BayLog.debug("%s Accepted: cli=%s", PigeonMultiplexer.this, clientCh);
                            agent.sendAcceptedLetter(rd, PigeonMultiplexer.this, new AsynchronousSocketChannelRudder(clientCh), true);
                        }

                        @Override
                        public void failed(Throwable e, Rudder serverRd) {
                            agent.sendErrorLetter(rd, PigeonMultiplexer.this, e, true);
                        }
                    });
        }
        catch(AcceptPendingException e) {
            // Another thread already accepting
        }
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        RudderState st = findRudderStateByKey(ChannelRudder.getChannel(rd));
        AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(rd).connect(
                addr, null, new CompletionHandler<Void, Void>() {

            @Override
            public void completed(Void result, Void attachment) {
                agent.sendConnectedLetter(rd, PigeonMultiplexer.this, true);
            }

            @Override
            public void failed(Throwable e, Void attachment) {
                agent.sendErrorLetter(rd, PigeonMultiplexer.this, e, true);
            }
        });
    }

    @Override
    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = getRudderState(rd);
        BayLog.debug("%s reqRead rd=%s state=%s", this, rd, state);
        if(state == null)
            return;

        boolean needRead = false;
        synchronized (state.reading) {
            if (!state.reading[0]) {
                needRead = true;
                state.reading[0] = true;
            }
        }

        BayLog.debug("%s needRead=%s", agent, needRead);
        if(needRead) {
            nextRead(state);
        }


        state.access();
    }

    @Override
    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = getRudderState(rd);
        BayLog.debug("%s reqWrite tag=%s state=%s len=%d", this, tag, state, buf.remaining());
        if(state == null) {
            throw new IOException("Invalid rudder");
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (state.writeQueue) {
            state.writeQueue.add(unt);
        }

        boolean needWrite = false;
        synchronized (state.writing) {
            if (!state.writing[0]) {
                needWrite = true;
                state.writing[0] = true;
            }
        }

        if(needWrite) {
            if(state.rudder instanceof AsynchronousFileChannelRudder)
                nextFileWrite(state);
            else
                nextNetworkWrite(state);
        }

        state.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState state = getRudderState(rd);
        if(state == null)
            return;

        state.end();
        state.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        RudderState st = findRudderStateByKey(ChannelRudder.getChannel(rd));
        if(st == null) {
            BayLog.debug("%s Rudder is closed: %s", this, rd);
            return;
        }
        else {
            try {
                rd.close();
            } catch (IOException e) {
                BayLog.error(e);
            }
            agent.sendClosedLetter(rd, this, true);
        }
    }

    @Override
    public void cancelRead(RudderState st) {

    }

    @Override
    public void cancelWrite(RudderState st) {

    }

    @Override
    public void nextRead(RudderState state) {
        if(state.rudder instanceof AsynchronousFileChannelRudder)
            nextFileRead(state);
        else
            nextNetworkRead(state);
    }

    @Override
    public void nextWrite(RudderState st) {
        if (st.rudder instanceof AsynchronousFileChannelRudder)
            nextFileWrite(st);
        else
            nextNetworkWrite(st);
    }

    @Override
    public boolean isNonBlocking() {
        return false;
    }

    @Override
    public void nextAccept(RudderState state) {
        reqAccept(state.rudder);
    }

    @Override
    public boolean useAsyncAPI() {
        return true;
    }

    @Override
    public void onBusy() {

    }

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private class ReadCompletionHandler implements CompletionHandler<Integer, Rudder> {
        final RudderState state;

        private ReadCompletionHandler(RudderState state) {
            this.state = state;
        }

        @Override
        public void completed(Integer n, Rudder rd) {
            BayLog.debug("%s read completed: rd=%s buf=%s", PigeonMultiplexer.this, rd, state.readBuf);
            state.readBuf.flip();
            agent.sendReadLetter(rd, PigeonMultiplexer.this, n, null, true);
        }

        @Override
        public void failed(Throwable e, Rudder rd) {
            agent.sendErrorLetter(rd, PigeonMultiplexer.this, e, true);
        }

    }

    private class WriteCompletionHandler implements CompletionHandler<Integer, Rudder> {
        final RudderState state;

        private WriteCompletionHandler(RudderState state) {
            this.state = state;
        }

        @Override
        public void completed(Integer n, Rudder rd) {
            agent.sendWroteLetter(rd, PigeonMultiplexer.this, n, true);
        }

        @Override
        public void failed(Throwable e, Rudder rd) {
            agent.sendErrorLetter(rd, PigeonMultiplexer.this, e, true);
        }
    }



    private void nextFileRead(RudderState state) {
        AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(state.rudder);
        state.readBuf.clear();
        ch.read(
                state.readBuf,
                state.bytesRead,
                state.rudder,
                new ReadCompletionHandler(state));
    }

    private void nextFileWrite(RudderState st) {
        WriteUnit unit = st.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d", this, unit.tag, unit.buf.limit());
        //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(unit.buf.limit() > 0) {
            AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(st.rudder);
            ch.write(
                    unit.buf,
                    st.bytesWrote,
                    st.rudder,
                    new WriteCompletionHandler(st));

        }
        else {
            new WriteCompletionHandler(st).completed(unit.buf.limit(), st.rudder);
        }
    }

    private void nextNetworkRead(RudderState state) {
        AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(state.rudder);
        BayLog.debug("%s Try to Read (rd=%s) (buf=%s) timeout=%d", this, state.rudder, state.readBuf, state.timeoutSec);
        state.readBuf.clear();
        if(state.timeoutSec > 0) {
            ch.read(
                    state.readBuf,
                    state.timeoutSec,
                    TimeUnit.SECONDS,
                    state.rudder,
                    new ReadCompletionHandler(state));
        }
        else {
            ch.read(
                    state.readBuf,
                    state.rudder,
                    new ReadCompletionHandler(state));
        }
    }

    private void nextNetworkWrite(RudderState st) {
        WriteUnit unit = st.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d rd=%s timeout=%d", this, unit.tag, unit.buf.limit(), st.rudder, st.timeoutSec);
       // BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(unit.buf.limit() > 0) {
            AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(st.rudder);
            ch.write(
                    unit.buf,
                    st.timeoutSec,
                    TimeUnit.SECONDS,
                    st.rudder,
                    new WriteCompletionHandler(st));

        }
        else {
            new WriteCompletionHandler(st).completed(unit.buf.limit(), st.rudder);
        }
    }
}
