package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.util.Valve;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ReadFileTaxi extends Taxi implements Valve {

    InputStream in;
    boolean chValid;
    DataListener dataListener;
    ByteBuffer buf;
    boolean running;

    public ReadFileTaxi(int bufsize) {
        this.buf = ByteBuffer.allocate(bufsize);
    }

    public void init(InputStream in, DataListener lis) {
        this.in = in;
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
    public synchronized void openValve() {
        //BayLog.debug("*** RESUME ******  %s", this);
        nextRun();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Implements Taxi
    ////////////////////////////////////////////////////////////////////////////////
    @Override
    protected synchronized void depart() {
        try {
            buf.clear();
            int readLen = in.read(buf.array(), 0, buf.capacity());
            if(readLen == -1) {
                if(!chValid)
                    throw new Sink();

                dataListener.notifyEof();
                close();
                return;
            }

            buf.position(readLen);
            buf.flip();

            NextSocketAction act = dataListener.notifyRead(buf, null);

            running = false;
            if(act == NextSocketAction.Continue)
                nextRun();
        }
        catch(Throwable e) {
            BayLog.error(e);
            close();
        }
    }


    private void nextRun() {
        if(running)
            throw new Sink("%s already running", this);
        running = true;
        //BayLog.debug("POST NEXT RUN: %s", this);
        TaxiRunner.post(this);
    }

    private void close() {
        try {
            in.close();
        } catch (IOException ex) {
            BayLog.error(ex);
            ex.printStackTrace();
        }
        chValid = false;
        dataListener.notifyClose();
    }
}
