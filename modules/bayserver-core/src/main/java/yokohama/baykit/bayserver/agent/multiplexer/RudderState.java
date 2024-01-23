package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.common.Rudder;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RudderState {

    public final Rudder rudder;
    public final DataListener listener;
    public final Transporter transporter;

    boolean accepted;
    long lastAccessTime;
    boolean closing;
    ByteBuffer readBuf = ByteBuffer.allocate(8192);
    public ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    public boolean valid;
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
        this.accepted = false;
        this.valid = true;
    }

    void access() {
        lastAccessTime = System.currentTimeMillis();
    }

    void invalidate() {
        valid = false;
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
