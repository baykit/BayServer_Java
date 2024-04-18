package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.CommandReceiver;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public abstract class JobMultiplexerBase extends MultiplexerBase implements Runnable, TimerHandler, Multiplexer {

    protected enum LetterType {
        Accept,
        Connect,
        Read,
        Write,
        CloseReq
    }

    protected static class Letter {
        LetterType type;
        Rudder rudder;
        int nBytes;
        Throwable err;
        Rudder clientRudder;

        public Letter(LetterType type, Rudder rudder) {
            this.type = type;
            this.rudder = rudder;
        }

        public Letter(LetterType type, Rudder rudder, int nBytes) {
            this.type = type;
            this.rudder = rudder;
            this.nBytes = nBytes;
        }

        public Letter(LetterType type, Rudder rudder, Rudder clientRudder) {
            this.type = type;
            this.rudder = rudder;
            this.clientRudder = clientRudder;
        }

        public Letter(LetterType type, Rudder rudder, Throwable err) {
            this.type = type;
            this.rudder = rudder;
            this.err = err;
        }
    }

    protected final ArrayList<Letter> letterQueue = new ArrayList<>();

    private final boolean anchorable;

    private Pipe pipe;
    private CommandReceiver commandReceiver;

    ////////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////////

    protected abstract void reqAccept(Rudder rd);

    protected abstract void nextRead(RudderState state);

    protected abstract void nextWrite(RudderState st);

        ////////////////////////////////////////////
    // Constructor
    ////////////////////////////////////////////

    public JobMultiplexerBase(GrandAgent agent, boolean anchorable) {
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
    public final void start() {
        new Thread(this).start();
    }

    @Override
    public final void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
        new Thread(() -> {
            while (!commandReceiver.closed) {
                commandReceiver.onPipeReadable();
            }
        }).start();
    }

    @Override
    public final void shutdown() {
        commandReceiver.end();
        closeAll();
    }


    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public final void onTimer() {
        closeTimeoutSockets();
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    protected void sendLetter(Letter let) {
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

    private void onAccept(Letter let) throws Throwable {
        Port p = BayServer.anchorablePortMap.get(let.rudder);

        try {
            if(let.err != null)
                throw let.err;

            p.onConnected(agent.agentId, let.clientRudder);
        }
        catch (HttpException e) {
            BayLog.error(e);
            try {
                let.clientRudder.close();
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

            BayLog.debug("%s wrote %d bytes rd=%s", this, let.nBytes, let.rudder);
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
                nextWrite(st);
            }
        }
        catch(IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, true);
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
            if(st.transporter.checkTimeout(st.rudder, (int)(now - st.lastAccessTime) / 1000)) {
                BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                closeList.add(st);
            }
        }

        for (RudderState c : closeList) {
            closeRudder(c);
        }
    }

    protected void onFree() {
        if(agent.aborted)
            return;

        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            reqAccept(rd);
        }
    }


    protected void nextAction(RudderState st, NextSocketAction act, boolean reading) {
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

    protected final void onError(Rudder rd, Throwable e) {
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

    protected final void onCloseReq(Letter let) {
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
