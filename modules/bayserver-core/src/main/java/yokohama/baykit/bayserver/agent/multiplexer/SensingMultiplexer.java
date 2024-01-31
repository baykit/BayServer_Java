package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.*;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import static java.nio.channels.SelectionKey.*;

/**
 * The purpose of SensingMultiplexer is to sense sockets, pipes, or files through the select/epoll/kqueue API.
 */
public class SensingMultiplexer extends MultiplexerBase implements Runnable, TimerHandler, Multiplexer {

    static class ChannelOperation {
        final Rudder rudder;
        int op;
        boolean close;

        ChannelOperation(Rudder rd, int op, boolean close) {
            if(rd == null)
                throw new NullPointerException();
            this.rudder = rd;
            this.op = op;
            this.close = close;
        }

        ChannelOperation(Rudder rd, int op) {
            this(rd, op, false);
        }
    }



    private final boolean anchorable;
    private final AcceptHandler acceptHandler;

    private Selector selector;
    private CommandReceiver commandReceiver;


    final ArrayList<ChannelOperation> operations = new ArrayList<>();

    public SensingMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        if(anchorable) {
            this.acceptHandler = new AcceptHandler(agent);
        }
        else {
            this.acceptHandler = null;
        }

        try {
            this.selector = Selector.open();
        }
        catch(IOException e) {
            BayLog.fatal(e);
            System.exit(1);
        }

        agent.addTimerHandler(this);
    }

    ////////////////////////////////////////////
    // Implements Thread
    ////////////////////////////////////////////
    @Override
    public void run() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        try {
            commandReceiver.comRecvChannel.configureBlocking(false);
            commandReceiver.comRecvChannel.register(selector, SelectionKey.OP_READ);

            // Set up unanchorable channel
            if(!anchorable) {
                for (Rudder rd : BayServer.unanchorablePortMap.keySet()) {
                    Port p = BayServer.unanchorablePortMap.get(rd);
                    DataListener lis = p.newDataListener(agent.agentId, rd);
                    Transporter tp = p.newTransporter(agent.agentId, rd);
                    RudderState st = new RudderState(rd, lis, tp);
                    addState(rd, st);
                    reqRead(rd);
                }
            }

            boolean busy = true;
            while (true) {
                if(acceptHandler != null) {
                    boolean testBusy = channelCount >= agent.maxInboundShips;
                    if (testBusy != busy) {
                        busy = testBusy;
                        if(busy) {
                            onBusy();
                        }
                        else {
                            onFree();
                        }
                    }
                }

                /*
                System.err.println("selecting...");
                selector.keys().forEach((key) -> {
                    System.err.println(this + " readable=" + (key.isValid() ? "" + key.interestOps() : "invalid "));
                });
                */

                if(agent.aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                int count;
                if (!agent.spinMultiplexer.isEmpty()) {
                    count = selector.selectNow();
                }
                else {
                    count = selector.select(agent.timeoutSec * 1000L);
                }

                if(agent.aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                //BayLog.debug(this + " select count=" + count);
                boolean processed = registerChannelOps() > 0;

                Set<SelectionKey> selKeys = selector.selectedKeys();
                if(selKeys.isEmpty()) {
                    processed |= agent.spinMultiplexer.processData();
                }

                for(Iterator<SelectionKey> it = selKeys.iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    it.remove();
                    //BayLog.debug(this + " selected key=" + key);
                    if(key.channel() == commandReceiver.comRecvChannel)
                        commandReceiver.onPipeReadable();
                    else if(key.isAcceptable())
                        acceptHandler.onAcceptable(key);
                    else
                        handleChannel(key);
                    processed = true;
                }

                if(!processed) {
                    // timeout check if there is nothing to do
                    agent.ring();
                }
            }
        }
        catch (Throwable e) {
            // If error occurs, grand agent ends
            BayLog.fatal(e);
        }
        finally {
            BayLog.info("%s end", this);
            agent.shutdown();
        }
    }


    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        new Thread(this).start();
    }

    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState chState = findRudderState(rd);
        BayLog.debug("%s reqConnect addr=%s rd=%s chState=%s", agent, addr, rd, chState);

        //rd.configureBlocking(false);
        ((SocketChannel)ChannelRudder.getChannel(rd)).connect(addr);

        if(!(addr instanceof InetSocketAddress)) {
            // Unix domain socket does not support connect operation
            NextSocketAction nextSocketAction = chState.transporter.onConnectable(chState);
            if(nextSocketAction == NextSocketAction.Continue)
                reqRead(rd);
        }
        else {
            addOperation(rd, OP_CONNECT);
        }
    }

    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToRead");
        RudderState st = findRudderState(rd);
        BayLog.debug("%s reqRead chState=%s", agent, st);
        addOperation(rd, OP_READ);

        if(st == null)
            return;

        st.access();
    }

    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToWrite");
        RudderState st = findRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s len=%d", agent, st, buf.remaining());
        if(st == null || st.closed) {
            throw new IOException("Invalid channel");
        }

        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (st.writeQueue) {
            st.writeQueue.add(unt);
        }
        addOperation(rd, OP_WRITE);

        st.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState st = findRudderState(rd);
        if(st == null)
            return;

        st.end();
        st.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState st = findRudderState(rd);
        BayLog.debug("%s reqClose chState=%s", agent, st);
        addOperation(rd, OP_WRITE, true);

        if(st == null)
            return;

        st.access();
    }

    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
    }

    public void shutdown() {
        commandReceiver.end();
        closeAll();
    }

    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public void onTimer() {
        closeTimeoutSockets();
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private void addOperation(Rudder rd, int op, boolean close) {
        synchronized (operations) {
            boolean found = false;
            for(ChannelOperation cop : operations) {
                if(cop.rudder == rd) {
                    cop.op |= op;
                    cop.close = cop.close || close;
                    found = true;
                    BayLog.debug("%s Update operation: %d(%s) rd=%s", agent, cop.op, opMode(cop.op), cop.rudder);
                }
            }
            if(!found) {
                BayLog.debug("%s Add operation: %d(%s) rd=%s", agent, op, opMode(op), rd);
                operations.add(new ChannelOperation(rd, op, close));
            }
        }
        BayLog.trace("%s wakeup", agent);
        selector.wakeup();
    }

    private void addOperation(Rudder rd, int op) {
        addOperation(rd, op, false);
    }

    private int registerChannelOps() {
        if(operations.isEmpty())
            return 0;

        // register channels to selector
        synchronized (operations) {
            int nch = operations.size();
            for (ChannelOperation cop : operations) {
                RudderState st = findRudderState(cop.rudder);
                if (st == null) {
                    BayLog.debug("%s rudder is closed");
                    continue;
                }

                SelectableChannel ch =  (SelectableChannel)ChannelRudder.getChannel(cop.rudder);
                BayLog.debug("%s register chState=%s register op=%d(%s) ch=%s", agent, st, cop.op, opMode(cop.op), ch);
                SelectionKey key = ch.keyFor(selector);
                if(key != null) {
                    int op = key.interestOps();
                    int newOp = op | cop.op;
                    BayLog.debug("Already registered op=%d(%s) update to %s", op, opMode(op), opMode(newOp));
                    key.interestOps(newOp);
                }
                else {
                    try {
                        ch.register(selector, cop.op);
                    } catch (ClosedChannelException e) {
                        BayLog.debug(e, "%s Cannot register operation (Channel is closed): %s ch=%s op=%d(%s) close=%b",
                                agent, st, cop.rudder, cop.op, opMode(cop.op), cop.close);
                    }
                }

                if(cop.close) {
                    if(st == null) {
                        BayLog.warn("%s chState=%s register close but ChannelState is null", agent, st);
                    }
                    else {
                        st.closing = true;
                    }
                }
            }
            operations.clear();
            return nch;
        }
    }

    private void handleChannel(SelectionKey key) {

        RudderState chStt;
        // ready for read
        SelectableChannel ch = key.channel();
        chStt = findRudderStateByKey(ch);
        if (chStt == null) {
            BayLog.warn("%s Channel state is not registered", agent);
            key.cancel();
            return;
        }

        BayLog.debug("%s chState=%s Waked up: readable=%b writable=%b connectable=%b",
                        agent, chStt, key.isReadable(), key.isWritable(), key.isConnectable());
        NextSocketAction nextSocketAction = null;

        try {
            if (chStt.closing) {
                nextSocketAction = NextSocketAction.Close;
            }
            else if (key.isAcceptable()) {
                onAcceptable(key);
            }
            else if (key.isConnectable()) {
                BayLog.debug("%s chState=%s socket connectable", agent, chStt);

                // Cancel connect operation
                int op = key.interestOps() & ~OP_CONNECT;
                key.interestOps(op);

                nextSocketAction = chStt.transporter.onConnectable(chStt);
                if(nextSocketAction == NextSocketAction.Read) {
                    // Write OP Off
                    op = key.interestOps() & ~OP_WRITE;
                    if (op != OP_READ)
                        key.cancel();
                    else
                        key.interestOps(op);
                }
            }
            else {
                // read or write
                if (key.isReadable()) {
                    BayLog.trace("%s chState=%s socket readable", agent, chStt);
                    nextSocketAction = chStt.transporter.onReadable(chStt);
                    //BayLog.debug("%s chState=%s readable result=%s", agent, chStt, nextSocketAction);
                    if(nextSocketAction == NextSocketAction.Write) {
                        key.interestOps(key.interestOps() | OP_WRITE);
                    }
                }

                if (nextSocketAction != NextSocketAction.Close && key.isWritable()) {
                    BayLog.trace("%s chState=%s socket writable", agent, chStt);
                    nextSocketAction = chStt.transporter.onWritable(chStt);
                    if(nextSocketAction == NextSocketAction.Read) {
                        // Handle as "Write Off"
                        int op = (key.interestOps() & ~OP_WRITE) | OP_READ;
                        if(op != OP_READ)
                            key.cancel();
                        else
                            key.interestOps(OP_READ);
                    }
                    BayLog.debug("%s write next state=%s", agent, nextSocketAction);
                }

                if(nextSocketAction == null)
                    throw new Sink("Unknown next action");

            }
        } catch (Throwable e) {
            if(e instanceof IOException) {
                BayLog.info("%s I/O Error: skt=%s", agent, ch);
            }
            else if(e instanceof Sink){
                BayLog.error("%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw (Sink)e;
            }
            else {
                BayLog.error(e, "%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw new Sink("Unhandled error: %s", e);
            }

            // Cannot handle Exception any more
            chStt.transporter.onError(chStt, e);
            nextSocketAction = NextSocketAction.Close;
        }

        chStt.access();
        boolean keyCancel = false;
        BayLog.trace("%s chState=%s next=%s", agent, chStt, nextSocketAction);
        switch(nextSocketAction) {
            case Close:
                closeRudder(chStt);
                keyCancel = true;
                break;

            case Suspend:
                keyCancel = true;

            case Read:
            case Write:
            case Continue:
                break; // do nothing
        }

        if(keyCancel) {
            BayLog.trace("%s cancel key chState=%s", agent, chStt);
            key.cancel();
        }
    }


    public void closeTimeoutSockets() {
        if(rudders.isEmpty())
            return;

        ArrayList<RudderState> closeList = new ArrayList<>();;
        synchronized (rudders) {
            long now = System.currentTimeMillis();
            for (RudderState st : rudders.values()) {
                if(st.transporter.checkTimeout(st, (int)(now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: rd=%s", agent, st.rudder);
                    closeList.add(st);
                }
            }
        }
        for (RudderState c : closeList) {
            closeRudder(c);
        }
    }

    private void onAcceptable(SelectionKey key) {

    }

    public synchronized void onBusy() {
        BayLog.debug("%s AcceptHandler:onBusy", agent);
        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            SelectionKey key = ((ServerSocketChannel)ChannelRudder.getChannel(rd)).keyFor(selector);
            if(key != null)
                key.cancel();
        }
    }

    public synchronized void onFree() {
        BayLog.debug("%s AcceptHandler:onFree isShutdown=%s", agent, agent.aborted);
        if(agent.aborted)
            return;

        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            try {
                ((ServerSocketChannel)ChannelRudder.getChannel(rd)).register(selector, SelectionKey.OP_ACCEPT);
            }
            catch(ClosedChannelException e) {
                BayLog.error(e);
            }
        }
    }

    public static String opMode(int mode) {
        String modeStr = null;
        if ((mode & OP_ACCEPT) != 0)
            modeStr = "OP_ACCEPT";
        if ((mode & OP_CONNECT) != 0)
            modeStr = (modeStr == null) ? "OP_CONNECT" : modeStr + "|OP_CONNECT";
        if ((mode & OP_READ) != 0)
            modeStr = (modeStr == null) ? "OP_READ" : modeStr + "|OP_READ";
        if ((mode & OP_WRITE) != 0)
            modeStr = (modeStr == null) ? "OP_WRTIE" : modeStr + "|OP_WRITE";
        return modeStr;
    }


}
