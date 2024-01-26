package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.*;
import yokohama.baykit.bayserver.common.InputStreamRudder;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SpinMultiplexer extends MultiplexerBase implements TimerHandler {

    public interface SpinListener {
        NextSocketAction lap(boolean spun[]);

        boolean checkTimeout(int durationSec);

        void close();
    }

    static class ListenerInfo {
        SpinListener listener;
        long lastAccess;

        public ListenerInfo(SpinListener listener, long lastAccess) {
            this.listener = listener;
            this.lastAccess = lastAccess;
        }
    }

    int spinCount;

    ArrayList<RudderState> runningList = new ArrayList<>();

    public SpinMultiplexer(GrandAgent agent) {
        super(agent);
        agent.addTimerHandler(this);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqRead(Rudder rd) {
        RudderState st = findRudderState(rd);
        synchronized (runningList) {
            if (!runningList.contains(st))
                runningList.add(st);
        }
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        RudderState st = findRudderState(rd);
        WriteUnit unit = new WriteUnit(buf, adr, tag, listener);
        synchronized (st.writeQueue) {
            st.writeQueue.add(unit);
        }
        synchronized (runningList) {
            if (runningList.contains(st))
                runningList.add(st);
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
            RudderState st = runningList.get(i);
            boolean spun[] = new boolean[1];
            NextSocketAction act;
            if (st.rudder instanceof InputStreamRudder) {
                act = lapRead(st, spun);
            } else {
                act = lapWrite(st, spun);
            }

            switch (act) {
                case Suspend:
                    removeList.add(i);
                    break;
                case Close:
                    removeList.add(i);
                    break;
                case Continue:
                    continue;
                default:
                    throw new Sink();
            }

            st.access();
            allSpun = allSpun & spun[0];
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

        ArrayList<Integer> removeList = new ArrayList<>();
        ;
        synchronized (rudders) {
            long now = System.currentTimeMillis();
            for (int i = rudders.size() - 1; i >= 0; i--) {
                RudderState st = rudders.get(i);
                if (st.listener.checkTimeout((int) (now - st.lastAccessTime) / 1000)) {
                    closeRudder(st);
                    removeList.add(i);
                }
            }
        }

        for (Integer i : removeList) {
            synchronized (rudders) {
                rudders.remove(i);
            }
        }
    }


    private NextSocketAction lapRead(RudderState st, boolean[] spun) {
        spun[0] = false;

        try {
            boolean eof = false;
            InputStream in = InputStreamRudder.getInputStream(st.rudder);
            int len = in.available();
            if (len == 0) {
                if (st.eofChecker != null)
                    eof = st.eofChecker.isEof();

                if (!eof) {
                    BayLog.debug("%s Spin read: No data", this);
                    spun[0] = true;
                    return NextSocketAction.Continue;
                } else {
                    BayLog.debug("%s Spin read: EOF\\(^o^)/", this);
                }
            }

            if (eof) {
                st.readBuf.clear();
            }
            else {
                if (len > st.readBuf.capacity())
                    len = st.readBuf.capacity();

                st.readBuf.clear();
                int readLen = in.read(st.readBuf.array(), 0, len);
                if (readLen > 0) {
                    st.readBuf.limit(readLen);
                }
            }

            return st.listener.notifyRead(st.readBuf, null);

        } catch (Exception e) {
            BayLog.error(e, "%s Error", this);
            closeRudder(st);
            return NextSocketAction.Close;
        }
    }

    private NextSocketAction lapWrite(RudderState st, boolean[] spun) {
        // Java did not support non-blocking write
        throw new Sink();
    }
}

