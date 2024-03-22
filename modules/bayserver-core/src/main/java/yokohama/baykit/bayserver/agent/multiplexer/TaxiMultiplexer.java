package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.taxi.Taxi;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class TaxiMultiplexer extends MultiplexerBase implements Multiplexer {

    public TaxiMultiplexer(GrandAgent agt) {
        super(agt);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        throw new Sink();
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        throw new Sink();
    }

    @Override
    public void reqRead(Rudder rd) {
        BayLog.debug("%s TaxiMpx reqRead rd=%s", this, rd);
        RudderState st = getRudderState(rd);
        nextRun(st);
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        BayLog.debug("%s TaxiMpx reqWrite rd=%s", this, rd);
        RudderState st = getRudderState(rd);
        nextRun(st);
    }

    @Override
    public void reqEnd(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqClose(Rudder rd) {
        BayLog.debug("%s TaxiMpx reqClose rd=%s", this, rd);
        RudderState st = getRudderState(rd);
        closeRudder(st);
    }

    @Override
    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        throw new Sink();
    }

    @Override
    public void shutdown() {
        closeAll();
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////


    protected void onTimer(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.access();

        int durationSec = (int)(System.currentTimeMillis() - st.lastAccessTime) / 1000;
        if (st.transporter.checkTimeout(st.rudder, durationSec))
            closeRudder(st);
    }

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private void nextRun(RudderState st) {
        BayLog.debug("%s POST NEXT RUN: %s", this, st);
        if(st == null)
            throw new NullPointerException();
        TaxiRunner.post(agent.agentId, new Taxi() {
            @Override
            protected void depart() {
                if(st.rudder instanceof ReadableByteChannelRudder)
                    nextRead(st.rudder);
                else
                    nextWrite(st.rudder);
            }

            @Override
            protected void onTimer() {
                st.transporter.checkTimeout(st.rudder, -1);
            }
        });
    }

    private void nextRead(Rudder rd) {
        RudderState st = getRudderState(rd);

        try {
            ByteBuffer buf = ByteBuffer.allocate(8192);
            int len = ((ReadableByteChannel)ChannelRudder.getChannel(rd)).read(buf);
            BayLog.debug("%s Read %d bytes rd=%s", this, len, rd);
            NextSocketAction act;
            if (len <= 0) {
                buf.limit(0);
                act = st.transporter.onRead(st.rudder, buf, null);
            }
            else {
                buf.flip();
                act = st.transporter.onRead(st.rudder, buf, null);
            }
            BayLog.debug("Next action: %s", act);
            switch(act) {
                case Read:
                case Continue:
                    nextRun(st);
                    break;
                case Close:
                    closeRudder(st);
                    break;
                default:
                    throw new Sink();

            }
        }
        catch(IOException e) {
            BayLog.error(e);
            closeRudder(st);
        }
        catch(RuntimeException | Error e) {
            BayLog.error(e);
            closeRudder(st);
            throw e;
        }
    }

    private void nextWrite(Rudder rd) {
        RudderState st = getRudderState(rd);
        st.access();

        try {
            ((WritableByteChannel)ChannelRudder.getChannel(rd)).write(st.readBuf);
        }
        catch(IOException e) {
            BayLog.error(e);
            closeRudder(st);
        }
        catch(RuntimeException | Error e) {
            BayLog.error(e);
            closeRudder(st);
            throw e;
        }
    }
}
