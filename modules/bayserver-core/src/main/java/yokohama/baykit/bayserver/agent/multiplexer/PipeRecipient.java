package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.Recipient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.Timer;
import java.util.TimerTask;

public class PipeRecipient implements Recipient {

    Pipe pipe;
    boolean blocking;

    public PipeRecipient() {
        try {
            pipe = Pipe.open();
        } catch (IOException e) {
            BayLog.fatal(e);
            System.exit(1);
        }

        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wakeup();
            }
        }, GrandAgent.SELECT_TIMEOUT_SEC * 1000L, GrandAgent.SELECT_TIMEOUT_SEC * 1000L);
    }

    @Override
    public boolean receive(boolean wait) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);

        if(!wait) {
            if (blocking) {
                blocking = false;
                pipe.source().configureBlocking(false);
            }
        }
        else {
            if (!blocking) {
                blocking = true;
                pipe.source().configureBlocking(true);
            }
        }
        int n = pipe.source().read(buf);

        return n > 0;
    }

    @Override
    public void wakeup() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        try {
            pipe.sink().write(buf);
        } catch (IOException e) {
            BayLog.fatal(e);
            throw new Sink("Pipe Error: %s", e);
        }
    }
}
