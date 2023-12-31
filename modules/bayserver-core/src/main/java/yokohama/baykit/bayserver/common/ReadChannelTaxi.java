package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.nio.channels.Channel;

public class ReadChannelTaxi extends Taxi implements Valve {

    int agentId;
    Channel input;
    ChannelListener channelListener;
    boolean chValid;
    boolean running;
    long startTime;

    public ReadChannelTaxi(int agtId) {
        this.agentId = agtId;
    }

    public void setChannelListener(Channel input, ChannelListener lis) {
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
    public synchronized void openReadValve() {
        BayLog.debug("%s Open read valve", this);
        nextRun();
    }

    @Override
    public synchronized void openWriteValve() {
        throw new Sink();
    }

    @Override
    public synchronized void destroy() {
        close();
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
