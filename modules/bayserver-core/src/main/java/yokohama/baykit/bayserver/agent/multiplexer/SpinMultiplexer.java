package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.InputStreamRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class SpinMultiplexer extends MultiplexerBase implements TimerHandler {

    abstract class Lapper {
        RudderState state;
        long lastAccess;

        // Return if spun (method do nothing)
        abstract boolean lap();
        abstract void next();

        Lapper(RudderState state) {
            this.state = state;
            access();
        }

        void access() {
            lastAccess = RoughTime.currentTimeMillis();
        }
    }

    int spinCount;

    ArrayList<Lapper> runningList = new ArrayList<>();

    public SpinMultiplexer(GrandAgent agent) {
        super(agent);
        agent.addTimerHandler(this);
    }

    public String toString() {
        return "SpinMpx[" + agent + "]";
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
        RudderState st = getRudderState(rd);
        if(st == null) {
            BayLog.error("%s Invalid rudder", this);
            return;
        }
        boolean needRead = false;
        synchronized (st.reading) {
            if (!st.reading[0]) {
                needRead = true;
                st.reading[0] = true;
            }
        }

        if(needRead) {
            nextRead(st);
        }
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        RudderState st = getRudderState(rd);
        if(st == null)
            throw new IOException("Invalid rudder");

        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (st.writeQueue) {
            st.writeQueue.add(unt);
        }
        st.access();

        boolean needWrite = false;
        synchronized (st.writing) {
            if (!st.writing[0]) {
                needWrite = true;
                st.writing[0] = true;
            }
        }

        if(needWrite) {
            nextWrite(st);
        }
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.finale = true;
    }

    @Override
    public void reqClose(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.closing = true;
        closeRudder(rd);
        agent.sendClosedLetter(rd, this, false);
    }

    @Override
    public void cancelRead(RudderState st) {
        synchronized (st.reading) {
            BayLog.debug("%s Reading off %s", SpinMultiplexer.this, st.rudder);
            st.reading[0] = false;
        }
    }

    @Override
    public void cancelWrite(RudderState st) {

    }

    @Override
    public void nextRead(RudderState st) {
        Lapper lpr;

        if(st.rudder instanceof AsynchronousFileChannelRudder) {
            lpr = new AsyncReadLapper(st);
        }
        else if(st.rudder instanceof InputStreamRudder) {
            lpr = new ReadStreamLapper(st);
        }
        else {
            throw new Sink("Spin read not supported for this rudder");
        }

        lpr.next();
        synchronized (runningList) {
            runningList.add(lpr);
        }

    }

    @Override
    public void nextWrite(RudderState st) {
        Lapper lpr = null;

        if(st.rudder instanceof AsynchronousFileChannelRudder) {
            lpr = new AsyncWriteLapper(st);
        }
        else {
            throw new Sink("Spin write not supported");
        }

        lpr.next();
        synchronized (runningList) {
            runningList.add(lpr);
        }
    }

    @Override
    public void nextAccept(RudderState state) {
        throw new Sink();
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

    ////////////////////////////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////////////////////////////
    @Override
    public void onTimer() {
        stopTimeoutSpins();
    }

    ////////////////////////////////////////////////////////////////////
    // Custom method
    ////////////////////////////////////////////////////////////////////
    public boolean isEmpty() {
        return runningList.isEmpty();
    }

    public boolean processData() {
        if (isEmpty())
            return false;

        boolean allSpun = true;
        ArrayList<Integer> removeList = new ArrayList<>();
        for (int i = runningList.size() - 1; i >= 0; i--) {
            Lapper lpr = runningList.get(i);
            RudderState st = lpr.state;
            boolean spun = lpr.lap();

            st.access();
            allSpun = allSpun & spun;
        }

        if (allSpun) {
            spinCount++;
            if (spinCount > 10) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    BayLog.error(e);
                }
            }
        } else {
            spinCount = 0;
        }

        removeList.forEach(i -> {
            synchronized (runningList) {
                runningList.remove(i.intValue());
            }
        });

        return true;
    }

    ////////////////////////////////////////////////////////////////////
    // Private method
    ////////////////////////////////////////////////////////////////////

    private void stopTimeoutSpins() {
        if (rudders.isEmpty())
            return;

        ArrayList<Object> removeList = new ArrayList<>();;
        synchronized (rudders) {
            long now = RoughTime.currentTimeMillis();
            for (Object key: rudders.keySet()) {
                RudderState st = rudders.get(key);
                if (st.transporter != null && st.transporter.checkTimeout(st.rudder, (int) (now - st.lastAccessTime) / 1000)) {
                    closeRudder(st.rudder);
                    removeList.add(key);
                }
            }
        }

        for (Object key : removeList) {
            synchronized (rudders) {
                rudders.remove(key);
            }
        }
    }

    private class AsyncReadLapper extends Lapper {

        private Future<Integer> curFuture;

        AsyncReadLapper(RudderState state) {
            super(state);
        }

        @Override
        boolean lap() {
            if (!curFuture.isDone()) {
                return true;
            }

            try {
                int len = curFuture.get();
                BayLog.debug("%s Spin read: %d bytes", SpinMultiplexer.this, len);

                if (len == -1) {
                    len = 0;
                    BayLog.debug("%s Spin read: EOF\\(^o^)/", SpinMultiplexer.this);
                    state.readBuf.limit(0);
                }

                state.readBuf.flip();
                agent.sendReadLetter(state.rudder, SpinMultiplexer.this, len, null, false);

            } catch (Exception e) {
                agent.sendErrorLetter(state.rudder, SpinMultiplexer.this, e, false);
            }

            return false;
        }

        @Override
        void next() {
            state.readBuf.clear();
            curFuture = AsynchronousFileChannelRudder.getAsynchronousFileChannel(state.rudder).read(state.readBuf, state.bytesRead);
            state.bytesRead += state.readBuf.limit();
        }
    }

    private class AsyncWriteLapper extends Lapper {

        Future<Integer> curFuture;

        AsyncWriteLapper(RudderState state) {
            super(state);
        }

        @Override
        public boolean lap() {
            if(!curFuture.isDone()) {
                return true;
            }

            try {
                int len = curFuture.get();
                BayLog.debug("%s wrote %d bytes", SpinMultiplexer.this, len);
                agent.sendWroteLetter(state.rudder, SpinMultiplexer.this, len, false);
            }
            catch (Exception e) {
                agent.sendErrorLetter(state.rudder, SpinMultiplexer.this, e, false);
            }

            return false;
        }

        @Override
        public void next() {
            WriteUnit unit = state.writeQueue.get(0);
            BayLog.debug("%s write req %d bytes", SpinMultiplexer.this, unit.buf.limit());
            curFuture = AsynchronousFileChannelRudder.getAsynchronousFileChannel(state.rudder).write(unit.buf, state.bytesWrote);
            state.bytesWrote += state.readBuf.limit();
        }
    }

    private class ReadStreamLapper extends Lapper {

        ReadStreamLapper(RudderState state) {
            super(state);
        }

        @Override
        public boolean lap() {
            try {
                boolean eof = false;
                InputStream in = InputStreamRudder.getInputStream(state.rudder);
                int len = in.available();
                if (len == 0) {
                    if (state.eofChecker != null)
                        eof = state.eofChecker.isEof();

                    if (!eof) {
                        BayLog.debug("%s Spin read: No data", this);
                        return true;
                    } else {
                        BayLog.debug("%s Spin read: EOF\\(^o^)/", this);
                    }
                }

                if (eof) {
                    len = 0;
                    state.readBuf.clear();
                }
                else {
                    if (len > state.readBuf.capacity())
                        len = state.readBuf.capacity();

                    state.readBuf.clear();
                    int readLen = in.read(state.readBuf.array(), 0, len);
                    if (readLen > 0) {
                        state.readBuf.limit(readLen);
                    }
                }

                agent.sendReadLetter(state.rudder, SpinMultiplexer.this, len, null, false);

            } catch (Exception e) {
                agent.sendErrorLetter(state.rudder, SpinMultiplexer.this, e, false);
            }

            return false;
        }

        @Override
        void next() {

        }
    }
}

