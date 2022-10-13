package baykit.bayserver.docker.builtin;

import baykit.bayserver.BayLog;
import baykit.bayserver.agent.transporter.DataListener;
import baykit.bayserver.util.Valve;
import baykit.bayserver.taxi.Taxi;
import baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WriteFileTaxi extends Taxi implements Valve {


    OutputStream out;
    boolean chValid;
    DataListener dataListener;
    protected ArrayList<ByteBuffer> writeQueue = new ArrayList<>();
    int count;

    public WriteFileTaxi(){

    }

    public void init(OutputStream out, DataListener lis) throws IOException {
        this.out = out;
        this.dataListener = lis;
        this.chValid = true;
    }


    @Override
    public String toString() {
        return super.toString() + " " + dataListener.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Valve
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void openValve() {
        nextRun();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Taxi
    ////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void depart() {
        try {
            while(true) {
                ByteBuffer buf;
                synchronized (writeQueue) {
                    if(writeQueue.isEmpty())
                        break;
                    buf = writeQueue.remove(0);
                }
                out.write(buf.array(), 0, buf.limit());

                boolean empty;
                synchronized (writeQueue) {
                    empty = writeQueue.isEmpty();
                }

                if (!empty)
                    nextRun();
            }
        }
        catch(Throwable e) {
            BayLog.error(e);
        }
    }

    public void post(byte[] data, int ofs, int len) {
        synchronized (writeQueue) {
            boolean empty = writeQueue.isEmpty();
            writeQueue.add(ByteBuffer.wrap(data, ofs, len));
            if(empty)
                openValve();
        }
    }

    private void nextRun() {
        TaxiRunner.post(this);
    }
}
