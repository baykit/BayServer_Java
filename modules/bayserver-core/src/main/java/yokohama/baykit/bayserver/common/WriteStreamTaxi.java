package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.nio.channels.Channel;

public class WriteStreamTaxi extends Taxi implements Valve {


    int agentId;
    Channel output;
    ChannelListener channelListener;
    boolean chValid;

    public WriteStreamTaxi(int agentId){
        this.agentId = agentId;
    }

    public void init(Channel out, ChannelListener lis) throws IOException {
        this.output = out;
        this.channelListener = lis;
        this.chValid = true;
    }

    /////////////////////////////////////////
    // Implements Valve
    /////////////////////////////////////////

    @Override
    public void openReadValve() {
        throw new Sink();
    }

    @Override
    public void openWriteValve() {
        nextRun();
    }

    /////////////////////////////////////////
    // Implements Taxi
    /////////////////////////////////////////
    @Override
    protected void depart() {
        try {
            channelListener.onWritable(output);
        }
        catch(IOException e) {
            channelListener.onError(output, e);
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

    }

    private void nextRun() {
        TaxiRunner.post(agentId, this);
    }

    private synchronized void close() {
        BayLog.debug("%s Close taxi", this);
        if(!chValid)
            return;

        try {
            output.close();
        }
        catch (IOException e) {
            BayLog.error(e, "%s Cannot close", this);
        }

        chValid = false;
        channelListener.onClosed(output);
    }
}
