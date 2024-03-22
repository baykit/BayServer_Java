package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RudderState {

    final Rudder rudder;
    final Transporter transporter;

    long lastAccessTime;
    boolean closing;
    final ByteBuffer readBuf;
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    public boolean reading[] = new boolean[]{false};
    public boolean writing[] = new boolean[]{false};
    public int bytesRead;
    public int bytesWrote;
    public boolean closed;
    public boolean finale;
    EOFChecker eofChecker;

    public RudderState(Rudder rd) {
        this(rd, null);
    }

    public RudderState(Rudder rd, Transporter tp) {
        if (rd == null)
            throw new NullPointerException();
        this.rudder = rd;
        this.transporter = tp;
        this.closed = false;
        if(tp != null) {
            this.readBuf = ByteBuffer.allocate(tp.getReadBufferSize());
        }
        else {
            this.readBuf = ByteBuffer.allocate(8192);
        }
    }

    void access() {
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
