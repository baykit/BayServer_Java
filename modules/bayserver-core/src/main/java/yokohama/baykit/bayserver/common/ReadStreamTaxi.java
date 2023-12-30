package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class ReadStreamTaxi extends Taxi implements Valve {

    int agentId;
    InputStream input;
    ChannelListener<Closeable> channelListener;
    boolean chValid;
    boolean running;
    long startTime;

    public ReadStreamTaxi(int agtId) {
        this.agentId = agtId;
    }

    public void setChannelListener(InputStream input, ChannelListener lis) {
        this.input = input;
        this.channelListener = lis;
        this.chValid = true;
    }

    @Override
    public String toString() {
        return "rd_stream_txi#" + taxiId;
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
            NextSocketAction act = channelListener.onReadable(input);

            running = false;
            switch(act) {
                case Continue:
                    nextRun();
                    break;
                case Close:
                    close();
                    break;
                default:
                    throw new Sink();

            }
        }
        catch(IOException e) {
            channelListener.onError(input, e);
            close();
        }
        catch(RuntimeException | Error e) {
            BayLog.error(e);
            close();
            throw e;
        }
    }

    @Override
    protected void onTimer() {
        int durationSec = (int)(System.currentTimeMillis() - startTime) / 1000;
        if (channelListener.checkTimeout(input, durationSec))
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
        BayLog.debug("%s Close taxi", this);
        if(!chValid)
            return;

        try {
            input.close();
        }
        catch (IOException e) {
            BayLog.error(e, "%s Cannot close", this);
        }

        chValid = false;
        channelListener.onClosed(input);
    }
}
