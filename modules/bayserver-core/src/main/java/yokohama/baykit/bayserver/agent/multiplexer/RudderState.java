package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;

public class RudderState {

    public final Rudder rudder;
    public final Transporter transporter;
    public Multiplexer multiplexer;

    long lastAccessTime;
    boolean closing;
    public final ByteBuffer readBuf;
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    public SelectionKey selectionKey;
    public boolean reading[] = new boolean[]{false};
    public boolean writing[] = new boolean[]{false};
    public int bytesRead;
    public int bytesWrote;
    public boolean closed;
    public boolean finale;
    EOFChecker eofChecker;
    public int timeoutSec;

    public RudderState(Rudder rd) {
        this(rd, null);
    }

    public RudderState(Rudder rd, Transporter tp) {
        this(rd, tp, 0);
    }

    public RudderState(Rudder rd, Transporter tp, int timeoutSec) {
        if (rd == null)
            throw new NullPointerException();
        this.rudder = rd;
        this.transporter = tp;
        this.closed = false;
        this.timeoutSec = timeoutSec;
        if(tp != null) {
            this.readBuf = ByteBuffer.allocate(tp.getReadBufferSize());
        }
        else {
            this.readBuf = ByteBuffer.allocate(8192);
        }
    }

    public void access() {
        lastAccessTime = System.currentTimeMillis();
    }

    void end() {
        finale = true;
    }

    @Override
    public String toString() {
        String str = super.toString();
        if (closing)
            str += " closing";
        return str;
    }
}
