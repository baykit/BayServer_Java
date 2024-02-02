package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RudderState {

    public final Rudder rudder;
    public final DataListener listener;
    public final Transporter transporter;

    long lastAccessTime;
    boolean closing;
    ByteBuffer readBuf = ByteBuffer.allocate(8192);
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    public boolean reading[] = new boolean[]{false};
    public boolean writing[] = new boolean[]{false};
    public int bytesRead;
    public int bytesWrote;
    public boolean closed;
    public boolean finale;
    EOFChecker eofChecker;

    public RudderState(Rudder rd, DataListener lis) {
        this(rd, lis, null);
    }

    public RudderState(Rudder rd, DataListener lis, Transporter tp) {
        if (rd == null)
            throw new NullPointerException();
        if (lis == null)
            throw new NullPointerException();
        this.rudder = rd;
        this.listener = lis;
        this.transporter = tp;
        this.closed = false;
    }

    void access() {
        lastAccessTime = System.currentTimeMillis();
    }

    void end() {
        finale = true;
    }

    @Override
    public String toString() {
        String str = "";
        if (listener != null)
            str += listener;
        else
            str += super.toString();
        if (closing)
            str += " closing";
        return str;
    }
}