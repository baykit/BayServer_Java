package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class WriteChannelTaxi extends Taxi {

    int agentId;
    WritableByteChannel output;
    ByteBuffer buf;

    public WriteChannelTaxi(int agentId, WritableByteChannel out, ByteBuffer buf) throws IOException {
        this.agentId = agentId;
        this.output = out;
        this.buf = buf;
    }

    @Override
    public String toString() {
        return super.toString() + "(WriteChannel on #agt" + agentId + ")";
    }

    /////////////////////////////////////////
    // Implements Taxi
    /////////////////////////////////////////
    @Override
    protected void depart() {
        try {
            output.write(buf);
        }
        catch(Throwable e) {
            BayLog.fatal(e);
            close();
            GrandAgent.get(agentId).shutdown();
        }
    }

    @Override
    protected void onTimer() {

    }

    private void nextRun() {
        TaxiRunner.post(agentId, this);
    }

    private synchronized void close() {
        BayLog.debug("%s Close", this);

        try {
            output.close();
        }
        catch (IOException e) {
            BayLog.error(e, "%s Cannot close", this);
        }
    }
}
