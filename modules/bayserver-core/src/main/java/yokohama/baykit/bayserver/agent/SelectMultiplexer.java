package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.*;

import static java.nio.channels.SelectionKey.*;

public class SelectMultiplexer extends Thread implements TimerHandler, Multiplexer {

    class ChannelState {
        final SelectableChannel ch;
        final ChannelListener listener;

        boolean accepted;
        long lastAccessTime;
        boolean closing;

        ChannelState(SelectableChannel ch, ChannelListener lis) {
            if(ch == null)
                throw new NullPointerException();
            if(lis == null)
                throw new NullPointerException();
            this.ch = ch;
            this.listener = lis;
            this.accepted = false;
        }

        void access() {
            lastAccessTime = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            String str = agent + " ";
            if (listener != null)
                str += listener;
            else
                str += super.toString();
            if(closing)
                str += " closing";
            return str;
        }
    }

    static class ChannelOperation {
        final SelectableChannel ch;
        int op;
        boolean close;

        ChannelOperation(SelectableChannel ch, int op, boolean close) {
            if(ch == null)
                throw new NullPointerException();
            this.ch = ch;
            this.op = op;
            this.close = close;
        }

        ChannelOperation(SelectableChannel ch, int op) {
            this(ch, op, false);
        }
    }


    int chCount;

    private final GrandAgent agent;
    private final boolean anchorable;
    private final AcceptHandler acceptHandler;

    private Selector selector;

    private CommandReceiver commandReceiver;


    final ArrayList<ChannelOperation> operations = new ArrayList<>();
    final Map<SelectableChannel, ChannelState> channels = new HashMap<>();

    public SelectMultiplexer(GrandAgent agent, boolean anchorable) {
        super("SelectMultiplexer: " + agent);

        this.agent = agent;
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
                for (DatagramChannel ch : BayServer.unanchorablePortMap.keySet()) {
                    Port p = BayServer.unanchorablePortMap.get(ch);
                    ChannelListener tp = p.newChannelListener(agent.agentId, ch);
                    addChannelListener(ch, tp);
                    reqStart(ch);
                    reqRead(ch);
                }
            }

            boolean busy = true;
            while (true) {
                if(acceptHandler != null) {
                    boolean testBusy = acceptHandler.chCount >= agent.maxInboundShips;
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
                if (!agent.spinHandler.isEmpty()) {
                    count = selector.selectNow();
                }
                else {
                    count = selector.select(agent.selectTimeoutSec * 1000L);
                }

                if(agent.aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                //BayLog.debug(this + " select count=" + count);
                boolean processed = registerChannelOps() > 0;

                Set<SelectionKey> selKeys = selector.selectedKeys();
                if(selKeys.isEmpty()) {
                    processed |= agent.spinHandler.processData();
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
                    for(TimerHandler th: agent.timerHandlers) {
                        th.onTimer();
                    }
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
    // Custom methods
    ////////////////////////////////////////////
    public void addChannelListener(SelectableChannel ch, ChannelListener lis) {
        ChannelState chState = new ChannelState(ch, lis);
        addChannelState(ch, chState);
        chState.access();
    }

    public void reqStart(SelectableChannel ch) {
        BayLog.debug("%s askToStart: ch=%s", agent, ch);

        ChannelState chState = findChannelState(ch);
        chState.accepted = true;
        //askToRead(ch);
    }

    public void reqConnect(SocketChannel ch, SocketAddress addr) throws IOException {
        if(ch == null)
            throw new NullPointerException();

        ChannelState chState = findChannelState(ch);
        BayLog.debug("%s askToConnect addr=%s ch=%s chState=%s", agent, addr, ch, chState);

        //ch.configureBlocking(false);
        ch.connect(addr);

        if(!(addr instanceof InetSocketAddress)) {
            // Unix domain socket does not support connect operation
            NextSocketAction nextSocketAction = chState.listener.onConnectable(ch);
            if(nextSocketAction == NextSocketAction.Continue)
                reqRead(ch);
        }
        else {
            addOperation(ch, OP_CONNECT);
        }
    }

    public void reqRead(SelectableChannel ch) {
        if(ch == null)
            throw new NullPointerException();

        //BayLog.debug("askToRead");
        ChannelState st = findChannelState(ch);
        BayLog.debug("%s askToRead chState=%s", agent, st);
        addOperation(ch, OP_READ);

        if(st == null)
            return;

        st.access();
    }

    public void reqWrite(SelectableChannel ch) {
        if(ch == null)
            throw new NullPointerException();

        //BayLog.debug("askToWrite");
        ChannelState st = findChannelState(ch);
        BayLog.debug("%s askToWrite chState=%s", agent, st);
        addOperation(ch, OP_WRITE);

        if(st == null)
            return;

        st.access();
    }

    public void reqClose(SelectableChannel ch) {
        if(ch == null)
            throw new NullPointerException();

        ChannelState st = findChannelState(ch);
        BayLog.debug("%s askToClose chState=%s", agent, st);
        addOperation(ch, OP_WRITE, true);

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

    private void addOperation(SelectableChannel ch, int op, boolean close) {
        synchronized (operations) {
            boolean found = false;
            for(ChannelOperation cop : operations) {
                if(cop.ch == ch) {
                    cop.op |= op;
                    cop.close = cop.close || close;
                    found = true;
                    BayLog.debug("%s Update operation: %d(%s) ch=%s", agent, cop.op, opMode(cop.op), cop.ch);
                }
            }
            if(!found) {
                BayLog.debug("%s Add operation: %d(%s) ch=%s", agent, op, opMode(op), ch);
                operations.add(new ChannelOperation(ch, op, close));
            }
        }
        BayLog.trace("%s wakeup", agent);
        selector.wakeup();
    }

    private void addOperation(SelectableChannel ch, int op) {
        addOperation(ch, op, false);
    }


    public void handleChannel(SelectionKey key) {

        ChannelState chStt;
        // ready for read
        SelectableChannel ch = key.channel();
        chStt = findChannelState(ch);
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

                nextSocketAction = chStt.listener.onConnectable(ch);
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
                    nextSocketAction = chStt.listener.onReadable(ch);
                    //BayLog.debug("%s chState=%s readable result=%s", agent, chStt, nextSocketAction);
                    if(nextSocketAction == NextSocketAction.Write) {
                        key.interestOps(key.interestOps() | OP_WRITE);
                    }
                }

                if (nextSocketAction != NextSocketAction.Close && key.isWritable()) {
                    BayLog.trace("%s chState=%s socket writable", agent, chStt);
                    nextSocketAction = chStt.listener.onWritable(ch);
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
            chStt.listener.onError(ch, e);
            nextSocketAction = NextSocketAction.Close;
        }

        chStt.access();
        boolean keyCancel = false;
        BayLog.trace("%s chState=%s next=%s", agent, chStt, nextSocketAction);
        switch(nextSocketAction) {
            case Close:
                closeChannel(ch, chStt);
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


    int registerChannelOps() {
        if(operations.isEmpty())
            return 0;

        // register channels to selector
        synchronized (operations) {
            int nch = operations.size();
            for (ChannelOperation cop : operations) {
                ChannelState st = findChannelState(cop.ch);
                BayLog.debug("%s register chState=%s register op=%d(%s) ch=%s", agent, st, cop.op, opMode(cop.op), cop.ch);
                SelectionKey key = cop.ch.keyFor(selector);
                if(key != null) {
                    int op = key.interestOps();
                    int newOp = op | cop.op;
                    BayLog.debug("Already registered op=%d(%s) update to %s", op, opMode(op), opMode(newOp));
                    key.interestOps(newOp);
                }
                else {
                    try {
                        cop.ch.register(selector, cop.op);
                    } catch (ClosedChannelException e) {
                        ChannelState cst = findChannelState(cop.ch);
                        BayLog.debug(e, "%s Cannot register operation (Channel is closed): %s ch=%s op=%d(%s) close=%b",
                                agent, cst != null ? cst.listener : null, cop.ch, cop.op, opMode(cop.op), cop.close);
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

    public void closeTimeoutSockets() {
        if(channels.isEmpty())
            return;

        ArrayList<Object[]> closeList = new ArrayList<>();;
        synchronized (channels) {
            long now = System.currentTimeMillis();
            for (SelectableChannel ch : channels.keySet()) {
                ChannelState st = findChannelState(ch);
                if(st.listener.checkTimeout(ch, (int)(now - st.lastAccessTime) / 1000)) {
                    BayLog.debug("%s timeout: skt=%s", agent, ch);
                    closeList.add(new Object[]{ch, st});
                }
            }
        }
        for (Object[] c : closeList) {
            closeChannel((SelectableChannel) c[0], (ChannelState) c[1]);
        }
    }

    public void closeAll() {

        for (SelectableChannel ch : new ArrayList<>(channels.keySet())) {
            ChannelState st = findChannelState(ch);
            closeChannel(ch, st);
        }
    }

    private void onAcceptable(SelectionKey key) {

    }

    private void closeChannel(SelectableChannel ch, ChannelState chState) {
        BayLog.debug("%s close ch %s chState=%s", agent, ch, chState);

        if (chState == null)
            chState = findChannelState(ch);

        try {
            ch.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }

        chState.listener.onClosed(ch);
        if(chState.accepted)
            acceptHandler.onClosed();

        removeChannelState(ch);
    }

    private void addChannelState(SelectableChannel ch, ChannelState chState) {
        BayLog.trace("%s add ch %s chState=%s", agent, ch, chState);
        synchronized (channels) {
            channels.put(ch, chState);
        }
        chCount++;
    }

    private void removeChannelState(SelectableChannel ch) {
        BayLog.trace("%s remove ch %s", agent, ch);
        synchronized (channels) {
            ChannelState cm = channels.remove(ch);
            //BayServer.debug(cm.tpt.ship() + " removed");
        }
        chCount--;
    }

    private ChannelState findChannelState(Channel ch) {
        synchronized (channels) {
            return channels.get(ch);
        }
    }

    public synchronized void onBusy() {
        BayLog.debug("%s AcceptHandler:onBusy", agent);
        for(ServerSocketChannel ch: BayServer.anchorablePortMap.keySet()) {
            SelectionKey key = ch.keyFor(selector);
            if(key != null)
                key.cancel();
        }
    }

    public synchronized void onFree() {
        BayLog.debug("%s AcceptHandler:onFree isShutdown=%s", agent, agent.aborted);
        if(agent.aborted)
            return;

        for(ServerSocketChannel ch: BayServer.anchorablePortMap.keySet()) {
            try {
                ch.register(selector, SelectionKey.OP_ACCEPT);
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
