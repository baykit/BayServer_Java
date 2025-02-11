package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.multiplexer.WriteUnit;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.RoughTime;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;

public class RudderState implements Reusable {

    public Rudder rudder;
    public Transporter transporter;
    public Multiplexer multiplexer;

    public long lastAccessTime;
    public boolean closing;
    public ByteBuffer readBuf;
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    public SelectionKey selectionKey;
    public boolean reading[] = new boolean[]{false};
    public boolean writing[] = new boolean[]{false};
    public int bytesRead;
    public int bytesWrote;
    public boolean finale;
    public EOFChecker eofChecker;
    public int timeoutSec;

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
        this.rudder = rd;
        this.transporter = tp;
        this.timeoutSec = timeoutSec;

        int bufsize;
        if(tp != null) {
            bufsize = tp.getReadBufferSize();
        }
        else {
            bufsize = 8192;
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
        return "RdState(rd=" + rudder + " bufsize="
                + (readBuf != null ? readBuf.capacity() : 0)
                + " closing=" + closing + ")";
    }

    ////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {
        rudder = null;
        transporter = null;
        multiplexer = null;

        lastAccessTime = 0;
        closing = false;
        readBuf.clear();
        writeQueue.clear();;
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

    public void end() {
        finale = true;
    }

}
