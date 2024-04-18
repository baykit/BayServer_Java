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

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public class PigeonMultiplexer extends JobMultiplexerBase {

    public PigeonMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent, anchorable);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(rd).connect(
                addr, null, new CompletionHandler<Void, Void>() {

            @Override
            public void completed(Void result, Void attachment) {
                sendLetter(new Letter(LetterType.Connect, rd));
            }

            @Override
            public void failed(Throwable e, Void attachment) {
                sendLetter(new Letter(LetterType.Connect, rd, e));
            }
        });
    }

    @Override
    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = getRudderState(rd);
        BayLog.debug("%s reqRead rd=%s state=%s", agent, rd, state);
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
        BayLog.debug("%s reqWrite tag=%s state=%s len=%d", agent, tag, state, buf.remaining());
        if(state == null || state.closed) {
            throw new IOException("Invalid rudder");
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (state.writeQueue) {
            state.writeQueue.add(unt);
        }
        state.access();

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
        sendLetter(new Letter(LetterType.CloseReq, rd));
    }

    @Override
    public boolean useAsyncAPI() {
        return true;
    }

    ////////////////////////////////////////////
    // Implements JobMultiplexerBase
    ////////////////////////////////////////////

    @Override
    protected void reqAccept(Rudder rd) {
        BayLog.debug("%s reqAccept rd=%s aborted=%b", agent, rd, agent.aborted);
        if (agent.aborted) {
            return;
        }

        try {
            AsynchronousSocketChannel ch = null;
            ((AsynchronousServerSocketChannel) ChannelRudder.getChannel(rd)).accept(
                    rd,
                    new CompletionHandler<AsynchronousSocketChannel, Rudder>() {
                        @Override
                        public void completed(AsynchronousSocketChannel clientCh, Rudder serverRd) {
                            sendLetter(new Letter(LetterType.Accept, serverRd, new AsynchronousSocketChannelRudder(clientCh)));
                        }

                        @Override
                        public void failed(Throwable e, Rudder serverRd) {
                            sendLetter(new Letter(LetterType.Accept, serverRd, e));
                        }
                    });
        }
        catch(AcceptPendingException e) {
            // Another thread already accepting
        }
    }

    @Override
    protected void nextRead(RudderState state) {
        if(state.rudder instanceof AsynchronousFileChannelRudder)
            nextFileRead(state);
        else
            nextNetworkRead(state);
    }

    @Override
    protected void nextWrite(RudderState st) {
        if (st.rudder instanceof AsynchronousFileChannelRudder)
            nextFileWrite(st);
        else
            nextNetworkWrite(st);
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private class ReadCompletionHandler implements CompletionHandler<Integer, Rudder> {
        @Override
        public void completed(Integer n, Rudder rd) {
            sendLetter(new Letter(LetterType.Read, rd, n));
        }

        @Override
        public void failed(Throwable e, Rudder rd) {
            sendLetter(new Letter(LetterType.Read, rd, e));
        }

    }

    private class WriteCompletionHandler implements CompletionHandler<Integer, Rudder> {
        @Override
        public void completed(Integer n, Rudder rd) {
            sendLetter(new Letter(LetterType.Write, rd, n));
        }

        @Override
        public void failed(Throwable e, Rudder rd) {
            sendLetter(new Letter(LetterType.Write, rd, e));
        }
    }



    private void nextFileRead(RudderState state) {
        AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(state.rudder);
        state.readBuf.clear();
        ch.read(
                state.readBuf,
                state.bytesRead,
                state.rudder,
                new ReadCompletionHandler());
    }

    private void nextFileWrite(RudderState st) {
        WriteUnit unit = st.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b", this, unit.tag, unit.buf.limit(), st.closed);
        //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(!st.closed && unit.buf.limit() > 0) {
            AsynchronousFileChannel ch = (AsynchronousFileChannel)ChannelRudder.getChannel(st.rudder);
            ch.write(
                    unit.buf,
                    st.bytesWrote,
                    st.rudder,
                    new WriteCompletionHandler());

        }
        else {
            new WriteCompletionHandler().completed(unit.buf.limit(), st.rudder);
        }
    }

    private void nextNetworkRead(RudderState state) {
        AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(state.rudder);
        BayLog.debug("%s Try to Read (rd=%s) (buf=%s) timeout=%d", agent, state.rudder, state.readBuf, agent.timeoutSec);
        state.readBuf.clear();
        ch.read(
                state.readBuf,
                agent.timeoutSec,
                TimeUnit.SECONDS,
                state.rudder,
                new ReadCompletionHandler());
    }

    private void nextNetworkWrite(RudderState st) {
        WriteUnit unit = st.writeQueue.get(0);
        BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b rd=%s timeout=%d", this, unit.tag, unit.buf.limit(), st.closed, st.rudder, agent.timeoutSec);
       // BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

        if(!st.closed && unit.buf.limit() > 0) {
            AsynchronousSocketChannel ch = AsynchronousSocketChannelRudder.getAsynchronousSocketChannel(st.rudder);
            ch.write(
                    unit.buf,
                    agent.timeoutSec,
                    TimeUnit.SECONDS,
                    st.rudder,
                    new WriteCompletionHandler());

        }
        else {
            new WriteCompletionHandler().completed(unit.buf.limit(), st.rudder);
        }
    }
}
