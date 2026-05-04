package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.Counter;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.RoughTime;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;

public class RudderState implements Reusable {

    public static int STATE_ID_NOCHECK = -1;
    public Rudder rudder;
    public Transporter transporter;
    public Multiplexer multiplexer;

    static Counter idCounter = new Counter();
    public int id;

    public long lastAccessTime;
    public boolean closing;
    public ByteBuffer readBuf;
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    /** True iff this state is currently queued in SpiderMultiplexer.tryWriteList,
     *  used to dedupe additions without paying for HashSet hashCode/equals. */
    public boolean inTryWriteList;
    /**
     * Sum of WriteUnit.initialSize for all units in writeQueue, maintained
     * by reqWrite/consumeOldestUnit so remaining() is O(1) instead of O(N)
     * over writeQueue. Slightly overestimates during partial writes (the
     * head unit's actual remaining may be less than its initialSize), which
     * is fine for the back-pressure threshold check.
     *
     * Without this counter, RudderState.remaining() showed up as ~40% of
     * CPU on JFR for proxy-h2 1MB c=256 because reqWrite calls it twice on
     * every post (the body of a 1MB response is 64 H2 DATA frames + 128
     * WindowUpdate posts = ~192 reqWrites/req, each scanning a writeQueue
     * of dozens of units = quadratic).
     */
    public int writeQueueBytes;
    public SelectionKey selectionKey;
    public boolean reading[] = new boolean[]{false};
    public boolean writing[] = new boolean[]{false};
    public int bytesRead;
    public int bytesWrote;
    public boolean finale;
    public EOFChecker eofChecker;
    public int timeoutSec;
    public int bufsize;

    public RudderState() {

    }

    public void init(Rudder rd) {
        init(rd, null);
    }

    public void init(Rudder rd, Transporter tp) {
        init(rd, tp, 0);
    }

    public void init(Rudder rd, Transporter tp, int timeoutSec) {
        if (rd == null)
            throw new NullPointerException();
        this.id = idCounter.next();
        this.rudder = rd;
        this.transporter = tp;
        this.timeoutSec = timeoutSec;

        if(tp != null) {
            this.bufsize = tp.getReadBufferSize();
        }
        else {
            this.bufsize = 8192;
        }

        boolean alloc = true;
        if(this.readBuf != null) {
            if(this.readBuf.capacity() >= bufsize) {
                alloc = false;
            }
        }
        if(alloc)
            this.readBuf = ByteBuffer.allocate(bufsize);
    }

    @Override
    public String toString() {
        return "RdState(id=" + id + " rd=" + rudder + " bufsize="
                + (readBuf != null ? readBuf.capacity() : 0)
                + " closing=" + closing + ")";
    }

    ////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {
        id = 0;
        rudder = null;
        transporter = null;
        multiplexer = null;

        lastAccessTime = 0;
        closing = false;
        readBuf.clear();
        writeQueue.clear();
        writeQueueBytes = 0;
        inTryWriteList = false;
        selectionKey = null;
        bytesRead = 0;
        bytesWrote = 0;
        finale = false;
        reading[0] = false;
        writing[0] = false;
        eofChecker = null;
        timeoutSec = 0;
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////

    public void access() {
        lastAccessTime = RoughTime.currentTimeMillis();
    }

    public int remaining() {
        return writeQueueBytes;
    }

    /**
     * Returns whether the internal write buffer still has room.
     *
     * The buffer capacity is the shipBufferSize parameter configured on the
     * Harbor docker; this returns true when the pending data in the write
     * queue is less than or equal to shipBufferSize.
     */
    public boolean bufferAvailable() {
        return remaining() <= BayServer.harbor.shipBufferSize();
    }

    public void end() {
        finale = true;
    }
}
