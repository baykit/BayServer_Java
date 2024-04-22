package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.agent.CommandReceiver;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public abstract class JobMultiplexerBase extends MultiplexerBase implements TimerHandler, Multiplexer {

    private final boolean anchorable;

    private Pipe pipe;
    private CommandReceiver commandReceiver;

    ////////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////////

    protected abstract void reqAccept(Rudder rd);

        ////////////////////////////////////////////
    // Constructor
    ////////////////////////////////////////////

    public JobMultiplexerBase(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        agent.addTimerHandler(this);

        try {
            this.pipe = Pipe.open();
        }
        catch(IOException e) {
            BayLog.fatal(e);
            System.exit(1);
        }
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public final void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
        new Thread(() -> {
            while (!commandReceiver.closed) {
                commandReceiver.onPipeReadable();
            }
        }).start();
    }

    @Override
    public final void shutdown() {
        commandReceiver.end();
        closeAll();
    }


    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public final void onTimer() {
        closeTimeoutSockets();
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    protected final void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        ArrayList<RudderState> closeList = new ArrayList<>();
        HashSet<RudderState> copied = null;
        synchronized (rudders) {
            copied = new HashSet<>(this.rudders.values());
        }

        long now = System.currentTimeMillis();

        for (RudderState st : copied) {
            if(st.transporter != null) {
                if (st.transporter.checkTimeout(st.rudder, (int) (now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                    closeList.add(st);
                }
            }
        }

        for (RudderState c : closeList) {
            closeRudder(c);
        }
    }

    public void onFree() {
        if(agent.aborted)
            return;

        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            reqAccept(rd);
        }
    }


    /*
    protected final void onError(Rudder rd, Throwable e) {
        RudderState st = getRudderState(rd);
        if (st == null || st.closed) {
            // channel is already closed
            BayLog.debug("%s Rudder is already closed: err=%s rd=%s", agent, e, rd);
            return;
        }

        BayLog.debug("%s Failed to read: %s: %s", agent, rd, e);
        if(!(e instanceof IOException)) {
            BayLog.fatal(e);
            agent.shutdown();
        }
        else {
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, true);
        }
    }
    */
}
