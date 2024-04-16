package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.CommandReceiver;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public class PigeonMultiplexer extends MultiplexerBase implements Runnable, TimerHandler, Multiplexer {

    enum LetterType {
        Accept,
        Connect,
        Read,
        Write,
        CloseReq
    }

    static class Letter {
        LetterType type;
        Rudder rudder;
        int nBytes;
        Throwable err;
        AsynchronousSocketChannel clientChannel;

        public Letter(LetterType type, Rudder rudder) {
            this.type = type;
            this.rudder = rudder;
        }

        public Letter(LetterType type, Rudder rudder, int nBytes) {
            this.type = type;
            this.rudder = rudder;
            this.nBytes = nBytes;
        }

        public Letter(LetterType type, Rudder rudder, AsynchronousSocketChannel clientChannel) {
            this.type = type;
            this.rudder = rudder;
            this.clientChannel = clientChannel;
        }

        public Letter(LetterType type, Rudder rudder, Throwable err) {
            this.type = type;
            this.rudder = rudder;
            this.err = err;
        }
    }

    ArrayList<Letter> letterQueue = new ArrayList<>();

    private final boolean anchorable;

    private Pipe pipe;
    private CommandReceiver commandReceiver;


    public PigeonMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        agent.addTimerHandler(this);

        try {
            this.pipe = Pipe.open();
        }
        catch(IOException e) {
            BayLog.fatal(e);
            System.exit(1);
        }
    }

    ////////////////////////////////////////////
    // Implements Runnable
    ////////////////////////////////////////////
    @Override
    public void run() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        ByteBuffer buf = ByteBuffer.allocate(4);
        try {
            // Set up unanchorable channel
            if(!anchorable) {
                for (Rudder rd : BayServer.unanchorablePortMap.keySet()) {
                    Port p = BayServer.unanchorablePortMap.get(rd);
                    p.onConnected(agent.agentId, rd);
                }
            }

            boolean busy = true;
            while (true) {
                boolean testBusy = isBusy();
                if (busy && !testBusy) {
                    busy = false;
                    onFree();
                }

                if(agent.aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                int count;
                if (agent.spinMultiplexer.isEmpty()) {
                    buf.clear();
                    int c = pipe.source().read(buf);
                }

                if(agent.aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                boolean processed = false;
                if(!agent.spinMultiplexer.isEmpty()) {
                    processed |= agent.spinMultiplexer.processData();
                }

                while(true) {
                    Letter let;
                    synchronized (letterQueue) {
                        if(letterQueue.isEmpty()) {
                            break;
                        }
                        let = letterQueue.remove(0);
                    }

                    NextSocketAction nextAct = null;
                    switch(let.type) {
                        case Accept:
                            onAccept(let);
                            break;

                        case Connect:
                            onConnect(let);
                            break;

                        case Read:
                            onRead(let);
                            break;

                        case Write:
                            onWrite(let);
                            break;

                        case CloseReq:
                            onCloseReq(let);
                            break;
                    }
                    processed = true;
                }

                if(!processed) {
                    // timeout check if there is nothing to do
                    agent.ring();
                }
            }
        }
        catch (Throwable e) {
            // If error occurs, grand agent ends
            BayLog.fatal(e);
        }
        finally {
            BayLog.info("%s end", this);
            agent.shutdown();
        }
    }


    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        new Thread(this).start();
    }

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

    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
        new Thread(() -> {
            while (!commandReceiver.closed) {
                commandReceiver.onPipeReadable();
            }
        }).start();
    }

    public void shutdown() {
        commandReceiver.end();
        closeAll();
    }

    @Override
    public boolean useAsyncAPI() {
        return true;
    }

    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public void onTimer() {
        closeTimeoutSockets();
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

    private void sendLetter(Letter let) {
        synchronized (letterQueue) {
            letterQueue.add(let);
        }
        ByteBuffer buf = ByteBuffer.allocate(4);
        try {
            pipe.sink().write(buf);
        }
        catch(IOException e) {
            BayLog.fatal(e);
            throw new Sink("Cannot write to pipe: %s", e);
        }
    }

    private void reqAccept(Rudder rd) {
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
                            sendLetter(new Letter(LetterType.Accept, serverRd, clientCh));
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

    private void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        ArrayList<RudderState> closeList = new ArrayList<>();
        HashSet<RudderState> copied = null;
        synchronized (rudders) {
            copied = new HashSet<>(this.rudders.values());
        }

        long now = System.currentTimeMillis();

        for (RudderState st : copied) {
            if(st.transporter.checkTimeout(st.rudder, (int)(now - st.lastAccessTime) / 1000)) {
                BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                closeList.add(st);
            }
        }

        for (RudderState c : closeList) {
            closeRudder(c);
        }
    }

    private void onFree() {
        if(agent.aborted)
            return;

        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            reqAccept(rd);
        }
    }


    private void nextAction(RudderState st, NextSocketAction act, boolean reading) {
        BayLog.debug("%s next action: %s (reading=%b)", this, act, reading);
        boolean cancel = false;

        switch(act) {
            case Continue:
                if(reading)
                    nextRead(st);
                break;

            case Read:
                nextRead(st);
                break;

            case Write:
                if(reading)
                    cancel = true;
                break;

            case Close:
                if(reading)
                    cancel = true;
                closeRudder(st);
                break;

            case Suspend:
                if(reading)
                    cancel = true;
                break;
        }

        if(cancel) {
            synchronized (st.reading) {
                BayLog.debug("%s Reading off %s", agent, st.rudder);
                st.reading[0] = false;
            }
        }

        st.access();
    }

    private void nextRead(RudderState state) {
        if(state.rudder instanceof AsynchronousFileChannelRudder)
            nextFileRead(state);
        else
            nextNetworkRead(state);
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
            st.readBuf.clear();
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
        BayLog.debug("%s call read OK", agent);
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

    private void onAccept(Letter let) throws Throwable {
        Port p = BayServer.anchorablePortMap.get(let.rudder);
        AsynchronousSocketChannelRudder clientRd = new AsynchronousSocketChannelRudder(let.clientChannel);

        try {
            if(let.err != null)
                throw let.err;

            p.onConnected(agent.agentId, clientRd);
        }
        catch (HttpException e) {
            BayLog.error(e);
            try {
                let.clientChannel.close();
            }
            catch (IOException ex) {
                BayLog.error(ex);
            }
        }

        if (!isBusy()) {
            reqAccept(let.rudder);
        }
    }

    private void onConnect(Letter let) throws Throwable {
        RudderState st = getRudderState(let.rudder);

        BayLog.debug("%s connected rd=%s", agent, let.rudder);
        NextSocketAction nextAct;
        try {
            if(let.err != null)
                throw let.err;

            nextAct = st.transporter.onConnect(st.rudder);
            BayLog.debug("%s nextAct=%s", agent, nextAct);
        }
        catch (IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAct = NextSocketAction.Close;
        }

        if(nextAct == NextSocketAction.Continue) {
            // Read more
            reqRead(let.rudder);
        }
        else {
            nextAction(st, nextAct, false);
        }
    }

    private void onRead(Letter let) throws Throwable {
        RudderState st = getRudderState(let.rudder);
        if (st == null || st.closed) {
            // channel is already closed
            BayLog.debug("%s Rudder is already closed: rd=%s", agent, let.rudder);
            return;
        }

        NextSocketAction nextAct;

        try {
            if(let.err != null)
                throw let.err;

            BayLog.debug("%s read %d bytes (rd=%s) st=%d buf=%s", agent, let.nBytes, let.rudder, st.hashCode(), st.readBuf);
            st.bytesRead += let.nBytes;

            if (let.nBytes <= 0) {
                st.readBuf.limit(0);
                nextAct = st.transporter.onRead(st.rudder, st.readBuf, null);
            }
            else {
                st.readBuf.flip();
                nextAct = st.transporter.onRead(st.rudder, st.readBuf, null);
            }

        }
        catch (IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAct = NextSocketAction.Close;
        }

        nextAction(st, nextAct, true);
    }

    private void onWrite(Letter let) throws Throwable{

        RudderState st = getRudderState(let.rudder);
        if (st == null || st.closed) {
            // channel is already closed
            BayLog.debug("%s Rudder is already closed: rd=%s", agent, let.rudder);
            return;
        }

        try {

            if(let.err != null) {
                throw let.err;
            }

            BayLog.debug("%s wrote %d bytes rd=%s", PigeonMultiplexer.this, let.nBytes, let.rudder);
            st.bytesWrote += let.nBytes;

            boolean writeMore = true;
            WriteUnit unit = st.writeQueue.get(0);
            if (unit.buf.hasRemaining()) {
                BayLog.debug("Could not write enough data buf=%s", unit.buf);
                writeMore = true;
            }
            else {
                consumeOldestUnit(st);
            }

            synchronized (st.writing) {
                if (st.writeQueue.isEmpty()) {
                    writeMore = false;
                    st.writing[0] = false;
                }
            }

            if (writeMore) {
                if (st.rudder instanceof AsynchronousFileChannelRudder)
                    nextFileWrite(st);
                else
                    nextNetworkWrite(st);
            }
        }
        catch(IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, true);
        }

    }

    private void onError(Rudder rd, Throwable e) {
        RudderState st = getRudderState(rd);
        if (st == null || st.closed) {
            // channel is already closed
            BayLog.debug("%s Rudder is already closed: err=%s rd=%s", agent, e, rd);
            return;
        }

        BayLog.debug("%s Failed to read: %s: %s", agent, rd, e);
        if(!(e instanceof IOException)) {
            BayLog.fatal(e);
            agent.shutdown();
        }
        else {
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, true);
        }
    }

    private void onCloseReq(Letter let) {
        BayLog.debug("%s reqClose rd=%s", agent, let.rudder);
        RudderState state = getRudderState(let.rudder);
        if (state == null) {
            BayLog.debug("%s Rudder state not found: rd=%s", agent, let.rudder);
            return;
        }

        closeRudder(state);
        state.access();
    }
}
