package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.InputStreamRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class SpinMultiplexer extends MultiplexerBase implements TimerHandler {

    public interface Lapper {
        NextSocketAction lap(boolean spun[]);
        void next(LapperInfo info);
    }

    private class AsyncReadLapper implements Lapper {

        private RudderState state;
        private Future<Integer> curFuture;

        public AsyncReadLapper(RudderState state) {
            this.state = state;
        }

        @Override
        public NextSocketAction lap(boolean[] spun) {
            spun[0] = false;

            if (!curFuture.isDone()) {
                return NextSocketAction.Continue;
            }

            spun[0] = true;
            try {
                int len = curFuture.get();
                BayLog.debug("%s Spin read: %d bytes", this, len);

                if (len == -1) {
                    spun[0] = true;
                    BayLog.debug("%s Spin read: EOF\\(^o^)/", this);
                    return state.listener.notifyEof();
                } else {
                    state.readBuf.flip();
                    NextSocketAction act = state.listener.notifyRead(state.readBuf, null);
                    return act;
                }

            } catch (Exception e) {
                BayLog.error(e, "%s Error", this);
                closeRudder(state);
                return NextSocketAction.Close;
            }
        }

        @Override
        public void next(LapperInfo info) {
            state.readBuf.clear();
            curFuture = AsynchronousFileChannelRudder.getAsynchronousFileChannel(state.rudder).read(state.readBuf, state.bytesRead);
            state.bytesRead += state.readBuf.limit();
        }
    }

    private class AsyncWriteLapper implements Lapper {

        RudderState state;
        Future<Integer> curFuture;

        public AsyncWriteLapper(RudderState state) {
            this.state = state;
        }

        @Override
        public NextSocketAction lap(boolean[] spun) {
            spun[0] = false;

            if(!curFuture.isDone()) {
                return NextSocketAction.Continue;
            }

            try {
                int len = curFuture.get();
                BayLog.debug("%s wrote %d bytes", this, len);

                WriteUnit unit;
                synchronized (state.writeQueue) {
                    unit = state.writeQueue.remove(0);
                }

                if (len != unit.buf.limit()) {
                    throw new IOException("Could not write enough data");
                }
                spun[0] = true;

                state.bytesWrote += len;

                unit.done();

                boolean writeMore = true;
                synchronized (state.writing) {
                    if (state.writeQueue.isEmpty()) {
                        writeMore = false;
                        state.writing[0] = false;
                    }
                }

                if(writeMore)
                    return NextSocketAction.Continue;
                else
                    return NextSocketAction.Suspend;
            }
            catch (Exception e) {
                BayLog.error(e, "%s Error", this);
                closeRudder(state);
                return NextSocketAction.Close;
            }

        }

        @Override
        public void next(LapperInfo info) {
            WriteUnit unit = state.writeQueue.get(0);
            BayLog.debug("%s write req %d bytes", this, unit.buf.limit());
            curFuture = AsynchronousFileChannelRudder.getAsynchronousFileChannel(state.rudder).write(unit.buf, state.bytesWrote);
            state.bytesWrote += state.readBuf.limit();
        }
    }

    private class ReadStreamLapper implements Lapper {
        RudderState state;

        public ReadStreamLapper(RudderState state) {
            this.state = state;
        }

        @Override
        public NextSocketAction lap(boolean[] spun) {
            spun[0] = false;

            try {
                boolean eof = false;
                InputStream in = InputStreamRudder.getInputStream(state.rudder);
                int len = in.available();
                if (len == 0) {
                    if (state.eofChecker != null)
                        eof = state.eofChecker.isEof();

                    if (!eof) {
                        BayLog.debug("%s Spin read: No data", this);
                        spun[0] = true;
                        return NextSocketAction.Continue;
                    } else {
                        BayLog.debug("%s Spin read: EOF\\(^o^)/", this);
                    }
                }

                if (eof) {
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

                return state.listener.notifyRead(state.readBuf, null);

            } catch (Exception e) {
                BayLog.error(e, "%s Error", this);
                closeRudder(state);
                return NextSocketAction.Close;
            }
        }

        @Override
        public void next(LapperInfo info) {

        }
    }

    static class LapperInfo {
        Lapper lapper;
        RudderState state;
        long lastAccess;

        public LapperInfo(Lapper listener, RudderState state) {
            this.lapper = listener;
            this.state = state;
            access();
        }

        void access() {
            lastAccess = System.currentTimeMillis();
        }

    }

    int spinCount;

    ArrayList<LapperInfo> runningList = new ArrayList<>();

    public SpinMultiplexer(GrandAgent agent) {
        super(agent);
        agent.addTimerHandler(this);
    }

    public String toString() {
        return "SpinMultiplexer[" + agent + "]";
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        throw new Sink();
    }

    @Override
    public void reqRead(Rudder rd) {
        RudderState st = findRudderState(rd);
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
            LapperInfo info;

            if(rd instanceof AsynchronousFileChannelRudder) {
                info = new LapperInfo(new AsyncReadLapper(st), st);
            }
            else if(rd instanceof InputStreamRudder) {
                info = new LapperInfo(new ReadStreamLapper(st), st);
            }
            else {
                throw new Sink("Spin read not supported for this rudder");
            }

            info.lapper.next(info);
            synchronized (runningList) {
                runningList.add(info);
            }
        }
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        RudderState st = findRudderState(rd);
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
            LapperInfo info = null;

            if(rd instanceof AsynchronousFileChannelRudder) {
                info = new LapperInfo(new AsyncWriteLapper(st), st);
            }
            else {
                throw new IOException("Spin write not supported");
            }

            info.lapper.next(info);
            synchronized (runningList) {
                runningList.add(info);
            }
        }
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState st = findRudderState(rd);
        st.finale = true;
    }

    @Override
    public void reqClose(Rudder rd) {
        RudderState st = findRudderState(rd);
        st.closing = true;
    }

    @Override
    public void shutdown() {
        closeAll();
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

    boolean processData() {
        if (isEmpty())
            return false;

        boolean allSpun = true;
        ArrayList<Integer> removeList = new ArrayList<>();
        for (int i = runningList.size() - 1; i >= 0; i--) {
            LapperInfo info = runningList.get(i);
            RudderState st = info.state;
            boolean spun[] = new boolean[1];
            NextSocketAction act = info.lapper.lap(spun);

            st.access();
            allSpun = allSpun & spun[0];

            switch (act) {
                case Suspend:
                    removeList.add(i);
                    break;
                case Close:
                    removeList.add(i);
                    break;
                case Continue:
                    info.lapper.next(info);
                    continue;
                default:
                    throw new Sink();
            }
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
            long now = System.currentTimeMillis();
            for (Object key: rudders.keySet()) {
                RudderState st = rudders.get(key);
                if (st.listener.checkTimeout((int) (now - st.lastAccessTime) / 1000)) {
                    closeRudder(st);
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

    private void cancelRead(RudderState st) {
        synchronized (st.reading) {
            BayLog.debug("%s Reading off %s", agent, st.rudder);
            st.reading[0] = false;
        }
    }
    private void cancelWrite(RudderState st) {
        synchronized (st.writing) {
            BayLog.debug("%s Writing off %s", agent, st.rudder);
            st.writing[0] = false;
        }
    }
}

