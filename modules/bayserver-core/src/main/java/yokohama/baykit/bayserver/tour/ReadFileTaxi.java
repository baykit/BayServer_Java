package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.util.Valve;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ReadFileTaxi extends Taxi implements Valve {

    int agentId;
    InputStream in;
    boolean chValid;
    DataListener dataListener;
    ByteBuffer buf;
    boolean running;
    long startTime;

    public ReadFileTaxi(int agtId, int bufsize) {
        this.agentId = agtId;
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
    protected void depart() {
        startTime = System.currentTimeMillis();
        try {
            buf.clear();
            int readLen = in.read(buf.array(), 0, buf.capacity());
            if(readLen == -1) {
                if(!chValid)
                    throw new Sink();

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
        catch(IOException e) {
            BayLog.debug(e);
            close();
        }
        catch(RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    @Override
    protected void onTimer() {
        int durationSec = (int)(System.currentTimeMillis() - startTime) / 1000;
        if (dataListener.checkTimeout(durationSec))
            close();
    }


    private void nextRun() {
        if(running)
            throw new Sink("%s already running", this);
        running = true;
        //BayLog.debug("POST NEXT RUN: %s", this);
        TaxiRunner.post(agentId, this);
    }

    private synchronized void close() {
        if(!chValid)
            return;

        dataListener.notifyEof();

        try {
            in.close();
        }
        catch (IOException ex) {
            BayLog.error(ex);
        }

        chValid = false;
        dataListener.notifyClose();
    }
}
