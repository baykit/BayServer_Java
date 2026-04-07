package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.*;
import yokohama.baykit.bayserver.rudder.NetworkFdRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.RoughTime;
import yokohama.baykit.bayserver.util.uring.IoUring;
import yokohama.baykit.bayserver.util.uring.NativeFd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * io_uring-based multiplexer using Panama FFI (JDK 22+).
 * Provides true async I/O via IORING_OP_ACCEPT/RECV/SEND/CONNECT/CLOSE.
 *
 * This class does NOT import java.lang.foreign directly.
 * All Panama FFI access goes through IoUring (in bayserver-uring module).
 */
public class RoverMultiplexer implements Multiplexer, TimerHandler, Recipient {

    private static final int RING_ENTRIES = 256;
    private static final long USERDATA_EVENTFD = -1L;

    ////////////////////////////////////////////
    // Operation types
    ////////////////////////////////////////////
    static final int OP_ACCEPT  = 1;
    static final int OP_CONNECT = 2;
    static final int OP_RECV    = 3;
    static final int OP_SEND    = 4;
    static final int OP_CLOSE   = 5;

    @FunctionalInterface
    interface Submission {
        void execute() throws IOException;
    }

    ////////////////////////////////////////////
    // In-flight operation context
    ////////////////////////////////////////////
    static class UringOp {
        int opType;
        Rudder rudder;

        UringOp set(int opType, Rudder rudder) {
            this.opType = opType;
            this.rudder = rudder;
            return this;
        }
    }

    ////////////////////////////////////////////
    // Fields
    ////////////////////////////////////////////
    private final GrandAgent agent;
    private final boolean anchorable;
    private final IoUring ring;

    // Rudder -> RudderState
    private final HashMap<Rudder, RudderState> stateMap = new HashMap<>();

    // userData -> in-flight operation
    private final HashMap<Long, UringOp> pendingOps = new HashMap<>();
    private final AtomicLong userDataSeq = new AtomicLong(1);

    // UringOp object pool (bounded by RING_ENTRIES)
    private final ArrayDeque<UringOp> opPool = new ArrayDeque<>(RING_ENTRIES);

    private int channelCount;

    // Pending operations queue (thread-safe, flushed in receive())
    private final ArrayList<Submission> pendingSubmissions = new ArrayList<>();
    // Double-buffer for flush: avoids allocating a new ArrayList each cycle
    private final ArrayList<Submission> processingSubmissions = new ArrayList<>();

    // Accept arm state
    private boolean acceptArmed = false;

    // Wakeup coalescing: avoid redundant eventfd write syscalls
    private final AtomicBoolean wokenUp = new AtomicBoolean(false);

    public RoverMultiplexer(GrandAgent agent, boolean anchorable) throws IOException {
        this.agent = agent;
        this.anchorable = anchorable;
        this.ring = new IoUring(RING_ENTRIES);

        agent.addTimerHandler(this);

        // Arm eventfd for wakeup
        ring.prepareEventFdRead(USERDATA_EVENTFD);
    }

    @Override
    public String toString() {
        return "RoverMpx[" + agent + "]";
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void addRudderState(Rudder rd, RudderState st) {
        BayLog.trace("%s add rd=%s chState=%s", agent, rd, st);
        st.multiplexer = this;
        stateMap.put(rd, st);
        channelCount++;
        st.access();
    }

    @Override
    public void removeRudderState(Rudder rd) {
        BayLog.trace("%s remove rd=%s", agent, rd);
        stateMap.remove(rd);
        channelCount--;
    }

    @Override
    public RudderState getRudderState(Rudder rd) {
        return stateMap.get(rd);
    }

    @Override
    public Transporter getTransporter(Rudder rd) {
        RudderState st = getRudderState(rd);
        return st != null ? st.transporter : null;
    }

    @Override
    public void reqAccept(Rudder rd) {
        if (rd == null)
            throw new NullPointerException();

        BayLog.debug("%s reqAccept rd=%s", agent, rd);

        synchronized (pendingSubmissions) {
            pendingSubmissions.add(() -> submitAccept(rd));
        }
        wakeupRing();
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if (rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqConnect addr=%s rd=%s chState=%s", agent, addr, rd, st);

        if (!(addr instanceof InetSocketAddress)) {
            throw new IOException("RoverMultiplexer only supports InetSocketAddress");
        }

        synchronized (pendingSubmissions) {
            pendingSubmissions.add(() -> submitConnect(rd, (InetSocketAddress) addr));
        }
        wakeupRing();
    }

    @Override
    public void reqRead(Rudder rd) {
        if (rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqRead chState=%s", agent, st);

        if (st == null) {
            BayLog.warn("%s reqRead: RudderState not found: %s", agent, rd);
            return;
        }

        synchronized (pendingSubmissions) {
            pendingSubmissions.add(() -> submitRecv(rd, st));
        }
        wakeupRing();

        st.access();
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
        if (rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s tag=%s len=%d", agent, st, tag, buf.remaining());

        if (st == null) {
            BayLog.warn("%s Channel is closed: %s", agent, rd);
            listener.dataConsumed();
            return;
        }

        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (st.writeQueue) {
            st.writeQueue.add(unt);
        }

        // Only submit the first send; nextWrite will handle subsequent units after completion
        if (!st.writing[0]) {
            st.writing[0] = true;
            synchronized (pendingSubmissions) {
                pendingSubmissions.add(() -> submitSend(rd, st));
            }
            wakeupRing();
        }

        st.access();
    }

    @Override
    public void reqTransfer(Rudder rd, Rudder fileRd, int ofs, int len, DataConsumeListener listener) {
        throw new Sink("reqTransfer not supported by RoverMultiplexer");
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState st = getRudderState(rd);
        if (st == null) {
            BayLog.warn("%s reqEnd: RudderState not found: %s", agent, rd);
            return;
        }

        st.end();
        st.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        BayLog.debug("%s reqClose rd=%s", agent, rd);
        if (rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        if (st == null) {
            BayLog.warn("%s RudderState not found: %s", agent, rd);
            return;
        }

        closeRudder(rd);
        agent.sendClosedLetter(rd, this, false);
        st.access();
    }

    @Override
    public void shutdown() {
        try {
            ring.wakeup();
        }
        catch (IOException e) {
            BayLog.error(e);
        }
    }

    @Override
    public boolean isNonBlocking() {
        return true;
    }

    @Override
    public boolean useAsyncAPI() {
        return false;
    }

    @Override
    public boolean useUringAPI() {
        return true;
    }

    @Override
    public void cancelRead(RudderState st) {
        st.reading[0] = false;
    }

    @Override
    public void cancelWrite(RudderState st) {
        st.writing[0] = false;
    }

    @Override
    public void nextAccept(RudderState state) {
    }

    @Override
    public void nextRead(RudderState st) {
        // Called from event loop thread — prepare SQE directly, no synchronization needed.
        // The SQE will be submitted in the next submitAndWait() call.
        try {
            submitRecv(st.rudder, st);
        }
        catch (IOException e) {
            BayLog.warn("%s SQ ring full on nextRead, retrying via reqRead: %s", this, e.getMessage());
            reqRead(st.rudder);
        }
    }

    @Override
    public void nextWrite(RudderState st) {
        // Called from event loop thread — prepare SQE directly, no synchronization needed.
        try {
            submitSend(st.rudder, st);
        }
        catch (IOException e) {
            BayLog.warn("%s SQ ring full on nextWrite, retrying: %s", this, e.getMessage());
            synchronized (pendingSubmissions) {
                pendingSubmissions.add(() -> submitSend(st.rudder, st));
            }
            wakeupRing();
        }
    }

    @Override
    public synchronized void onBusy() {
        BayLog.debug("%s onBusy", agent);
        acceptArmed = false;
    }

    @Override
    public synchronized void onFree() {
        BayLog.debug("%s onFree aborted=%s", agent, agent.aborted);
        if (agent.aborted)
            return;

        for (Rudder rd : agent.anchorableRudders()) {
            reqAccept(rd);
        }
        acceptArmed = true;
    }

    @Override
    public boolean consumeOldestUnit(RudderState st) {
        WriteUnit u;
        synchronized (st.writeQueue) {
            if (st.writeQueue.isEmpty())
                return false;
            u = st.writeQueue.remove(0);
        }
        u.done();
        return true;
    }

    @Override
    public void closeRudder(Rudder rd) {
        BayLog.debug("%s closeRd %s", agent, rd);
        // Release pooled native buffers for this fd
        int fd = getFd(rd);
        if (fd >= 0) {
            ring.releaseFdBuffers(fd);
        }
        try {
            rd.close();
        }
        catch (AsynchronousCloseException e) {
            BayLog.debug("Close error: %s", e);
        }
        catch (IOException e) {
            BayLog.error(e);
        }
    }

    @Override
    public boolean isBusy() {
        return channelCount >= agent.maxInboundShips;
    }

    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////

    @Override
    public void onTimer() {
        closeTimeoutSockets();
    }

    ////////////////////////////////////////////
    // Implements Recipient
    ////////////////////////////////////////////

    @Override
    public boolean receive(boolean wait) throws IOException {
        // Reset wakeup flag so new submissions can trigger wakeup
        wokenUp.set(false);

        // Flush cross-thread submissions (reqRead/reqWrite/reqAccept from other threads)
        flushSubmissions();

        int pending = ring.pendingSqeCount();

        // Fast path: CQEs already in the CQ ring (mmap'd memory, no syscall needed)
        if (ring.availableCqeCount() > 0) {
            // Submit pending SQEs non-blocking if any, then process CQEs
            if (pending > 0)
                ring.submit();
            return processCompletions() > 0;
        }

        // Slow path: no CQEs available, need io_uring_enter syscall
        if (wait) {
            ring.submitAndWait(10);  // timeout 10 seconds for timer handling
        }
        else {
            if (pending > 0)
                ring.submit();
        }

        return processCompletions() > 0;
    }

    private int processCompletions() {
        int count = 0;
        long[] cqe;
        while ((cqe = ring.pollCompletion()) != null) {
            long userData = cqe[0];
            int res = (int) cqe[1];
            count++;

            if (userData == USERDATA_EVENTFD) {
                try {
                    ring.prepareEventFdRead(USERDATA_EVENTFD);
                }
                catch (IOException e) {
                    BayLog.error(e, "%s Failed to re-arm eventfd", this);
                }
                continue;
            }

            UringOp op = pendingOps.remove(userData);
            if (op == null) {
                BayLog.warn("%s Unknown userData in CQE: %d", this, userData);
                continue;
            }

            dispatchCompletion(op, res);
            releaseOp(op);
        }
        return count;
    }

    @Override
    public void wakeup() {
        try {
            ring.wakeup();
        }
        catch (IOException e) {
            BayLog.error(e);
        }
    }

    ////////////////////////////////////////////
    // Private: wakeup helper
    ////////////////////////////////////////////

    private void wakeupRing() {
        if (wokenUp.compareAndSet(false, true)) {
            try {
                ring.wakeup();
            }
            catch (IOException e) {
                BayLog.error(e);
            }
        }
    }

    ////////////////////////////////////////////
    // Private: SQE submission methods
    ////////////////////////////////////////////

    private void submitAccept(Rudder serverRd) throws IOException{
        int serverFd;
        try {
            serverFd = IoUring.getFdFromChannel(ChannelRudder.getChannel(serverRd));
        }
        catch (IOException e) {
            BayLog.error(e, "%s Failed to get fd for accept", this);
            return;
        }

        long ud = userDataSeq.getAndIncrement();
        ring.prepareAccept(serverFd, ud);

        pendingOps.put(ud, obtainOp(OP_ACCEPT, serverRd));
    }

    private void submitConnect(Rudder rd, InetSocketAddress addr)  throws IOException{
        int fd = getFd(rd);
        long ud = userDataSeq.getAndIncrement();
        ring.prepareConnect(fd, addr, ud);

        pendingOps.put(ud, obtainOp(OP_CONNECT, rd));
    }

    private void submitRecv(Rudder rd, RudderState st)  throws IOException{
        int fd = getFd(rd);
        int bufSize = st.readBuf.capacity();
        long ud = userDataSeq.getAndIncrement();
        ring.prepareRecv(fd, bufSize, ud);

        pendingOps.put(ud, obtainOp(OP_RECV, rd));
        st.reading[0] = true;
    }

    private void submitSend(Rudder rd, RudderState st)  throws IOException{
        int fd = getFd(rd);
        WriteUnit wUnit;
        synchronized (st.writeQueue) {
            if (st.writeQueue.isEmpty())
                return;
            wUnit = st.writeQueue.get(0);
        }

        long ud = userDataSeq.getAndIncrement();
        ring.prepareSend(fd, wUnit.buf, ud);

        pendingOps.put(ud, obtainOp(OP_SEND, rd));
    }

    ////////////////////////////////////////////
    // Private: CQE completion dispatch
    ////////////////////////////////////////////

    private void dispatchCompletion(UringOp op, int res) {
        switch (op.opType) {
            case OP_ACCEPT:
                onAcceptComplete(op, res);
                break;
            case OP_CONNECT:
                onConnectComplete(op, res);
                break;
            case OP_RECV:
                onRecvComplete(op, res);
                break;
            case OP_SEND:
                onSendComplete(op, res);
                break;
            case OP_CLOSE:
                onCloseComplete(op, res);
                break;
            default:
                BayLog.warn("%s Unknown op type: %d", this, op.opType);
        }
    }

    private void onAcceptComplete(UringOp op, int res) {
        Rudder serverRd = op.rudder;

        if (res < 0) {
            int errno = -res;
            BayLog.error("%s Accept failed: errno=%d", this, errno);
            agent.sendErrorLetter(serverRd, this, new IOException("accept failed: errno=" + errno), false);
        }
        else {
            int clientFd = res;
            BayLog.debug("%s Accepted fd=%d", agent, clientFd);

            if (agent.aborted) {
                try { NetworkFdRudder.closeFd(clientFd); } catch (IOException e) { BayLog.warn("%s closeFd failed: %s", this, e.getMessage()); }
                return;
            }

            NetworkFdRudder clientRd = new NetworkFdRudder(clientFd);
            try {
                clientRd.setNonBlocking();
                NativeFd.setTcpNoDelay(clientFd, true);
            }
            catch (IOException e) {
                BayLog.error(e, "%s Failed to configure accepted fd", this);
                try { clientRd.close(); } catch (IOException e2) { BayLog.warn("%s close failed: %s", this, e2.getMessage()); }
                return;
            }

            agent.sendAcceptedLetter(serverRd, this, clientRd, false);
        }

        // Re-arm accept if not busy
        if (acceptArmed && !isBusy()) {
            try {
                submitAccept(serverRd);
            }
            catch (IOException e) {
                BayLog.warn("%s SQ ring full on re-arm accept, retrying via reqAccept: %s", this, e.getMessage());
                reqAccept(serverRd);
            }
        }
    }

    private void onConnectComplete(UringOp op, int res) {
        if (res < 0) {
            agent.sendErrorLetter(op.rudder, this, new IOException("connect failed: errno=" + (-res)), false);
        }
        else {
            agent.sendConnectedLetter(op.rudder, this, false);
        }
    }

    private void onRecvComplete(UringOp op, int res) {
        RudderState st = getRudderState(op.rudder);
        if (st == null) {
            BayLog.warn("%s rudder already closed for recv completion: %s", this, op.rudder);
            return;
        }

        if (res < 0) {
            agent.sendErrorLetter(op.rudder, this, new IOException("recv failed: errno=" + (-res)), false);
        }
        else {
            // Copy native buffer to state.readBuf via per-fd recv buffer pool
            int fd = getFd(op.rudder);
            ring.copyRecvData(fd, st.readBuf, res);
            BayLog.debug("%s recv %d bytes rd=%s", this, res, op.rudder);

            agent.sendReadLetter(op.rudder, this, res, null, false);
        }
    }

    private void onSendComplete(UringOp op, int res) {
        if (res < 0) {
            agent.sendErrorLetter(op.rudder, this, new IOException("send failed: errno=" + (-res)), false);
        }
        else {
            // Advance the WriteUnit's buffer position so hasRemaining() reflects progress
            RudderState st = getRudderState(op.rudder);
            if (st == null) {
                BayLog.warn("%s onSendComplete: RudderState not found: %s", this, op.rudder);
            }
            else {
                synchronized (st.writeQueue) {
                    if (!st.writeQueue.isEmpty()) {
                        WriteUnit wUnit = st.writeQueue.get(0);
                        if (wUnit.buf != null) {
                            int newPos = wUnit.buf.position() + res;
                            if (newPos > wUnit.buf.limit())
                                newPos = wUnit.buf.limit();
                            wUnit.buf.position(newPos);
                        }
                    }
                }
            }
            agent.sendWroteLetter(op.rudder, this, res, false);
        }
    }

    private void onCloseComplete(UringOp op, int res) {
        agent.sendClosedLetter(op.rudder, this, false);
    }

    ////////////////////////////////////////////
    // Private: flush pending submissions
    ////////////////////////////////////////////

    private void flushSubmissions() {
        synchronized (pendingSubmissions) {
            if (pendingSubmissions.isEmpty())
                return;
            processingSubmissions.addAll(pendingSubmissions);
            pendingSubmissions.clear();
        }
        while (!processingSubmissions.isEmpty()) {
            try {
                processingSubmissions.get(0).execute();
                processingSubmissions.remove(0);
            }
            catch (IOException e) {
                BayLog.warn("%s Submission failed, requeueing %d submissions: %s",
                        this, processingSubmissions.size(), e.getMessage());
                // Put remaining submissions back at the front of pendingSubmissions
                synchronized (pendingSubmissions) {
                    pendingSubmissions.addAll(0, processingSubmissions);
                }
                processingSubmissions.clear();
                break;
            }
        }
    }

    ////////////////////////////////////////////
    // Private: UringOp pool
    ////////////////////////////////////////////

    private UringOp obtainOp(int opType, Rudder rudder) {
        UringOp op = opPool.pollFirst();
        if (op == null) {
            op = new UringOp();
        }
        return op.set(opType, rudder);
    }

    private void releaseOp(UringOp op) {
        op.rudder = null; // help GC
        opPool.offerFirst(op);
    }

    ////////////////////////////////////////////
    // Private: fd extraction helper
    ////////////////////////////////////////////

    private int getFd(Rudder rd) {
        if (rd instanceof NetworkFdRudder) {
            return ((NetworkFdRudder) rd).fd();
        }
        else if (rd instanceof ChannelRudder) {
            try {
                return IoUring.getFdFromChannel(ChannelRudder.getChannel(rd));
            }
            catch (IOException e) {
                throw new Sink("Failed to get fd from channel: %s: %s", rd, e.getMessage());
            }
        }
        else {
            throw new Sink("Unsupported rudder type: " + rd.getClass());
        }
    }

    ////////////////////////////////////////////
    // Private: timeout management
    ////////////////////////////////////////////

    private void closeTimeoutSockets() {
        if (stateMap.isEmpty())
            return;

        long now = RoughTime.currentTimeMillis();
        ArrayList<Rudder> timeoutRudders = null;

        for (RudderState st : stateMap.values()) {
            if (st.transporter != null && st.transporter.checkTimeout(st.rudder, (int) (now - st.lastAccessTime) / 1000)) {
                if (timeoutRudders == null) timeoutRudders = new ArrayList<>();
                timeoutRudders.add(st.rudder);
            }
        }

        if (timeoutRudders != null) {
            for (Rudder rd : timeoutRudders) {
                BayLog.debug("%s timeout: rd=%s", agent, rd);
                reqClose(rd);
            }
        }
    }
}
