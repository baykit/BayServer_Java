package baykit.bayserver.agent;

import baykit.bayserver.BayLog;
import baykit.bayserver.Sink;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static baykit.bayserver.agent.NextSocketAction.*;
import static java.nio.channels.SelectionKey.*;

public class NonBlockingHandler {

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

    GrandAgent agent;

    final ArrayList<ChannelOperation> operations = new ArrayList<>();
    final Map<SelectableChannel, ChannelState> channels = new HashMap<>();

    public NonBlockingHandler(GrandAgent agent) {
        this.agent = agent;
    }

    public ChannelState addChannelListener(SelectableChannel ch, ChannelListener lis) {
        ChannelState chState = new ChannelState(ch, lis);
        addChannelState(ch, chState);
        chState.access();
        return chState;
    }

    public void askToStart(SelectableChannel ch) {
        BayLog.debug("%s askToStart: ch=%s", agent, ch);

        ChannelState chState = findChannelState(ch);
        chState.accepted = true;
        //askToRead(ch);
    }

    public void askToConnect(SocketChannel ch,  SocketAddress addr) throws IOException {
        if(ch == null)
            throw new NullPointerException();

        ChannelState chState = findChannelState(ch);
        BayLog.debug("%s askToConnect addr=%s ch=%s chState=%s", agent, addr, ch, chState);

        //ch.configureBlocking(false);
        ch.connect(addr);
        addOperation(ch, OP_CONNECT);
    }

    public void askToRead(SelectableChannel ch) {
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

    public void askToWrite(SelectableChannel ch) {
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

    public void askToClose(SelectableChannel ch) {
        if(ch == null)
            throw new NullPointerException();

        ChannelState st = findChannelState(ch);
        BayLog.debug("%s askToClose chState=%s", agent, st);
        addOperation(ch, OP_WRITE, true);

        if(st == null)
            return;

        st.access();
    }

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
        agent.selector.wakeup();
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
                nextSocketAction = Close;
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
                if(nextSocketAction == Continue)
                    askToRead(ch);
            }
            else {
                // read or write
                if (key.isReadable()) {
                    BayLog.trace("%s chState=%s socket readable", agent, chStt);
                    nextSocketAction = chStt.listener.onReadable(ch);
                    //BayLog.debug("%s chState=%s readable result=%s", agent, chStt, nextSocketAction);
                    if(nextSocketAction == Write) {
                        key.interestOps(key.interestOps() | OP_WRITE);
                    }
                }

                if (nextSocketAction != Close && key.isWritable()) {
                    BayLog.trace("%s chState=%s socket writable", agent, chStt);
                    nextSocketAction = chStt.listener.onWritable(ch);
                    if(nextSocketAction == Read) {
                        // Handle as "Write Off"
                        int op = key.interestOps() & ~OP_WRITE;
                        if(op != OP_READ)
                            key.cancel();
                        else
                            key.interestOps(op);
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
                BayLog.info("%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw (Sink)e;
            }
            else {
                BayLog.info("%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw new Sink("Unhandled error: %s", e);
            }

            // Cannot handle Exception any more
            chStt.listener.onError(ch, e);
            nextSocketAction = Close;
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
                SelectionKey key = cop.ch.keyFor(agent.selector);
                if(key != null) {
                    int op = key.interestOps();
                    int newOp = op | cop.op;
                    BayLog.debug("Already registered op=%d(%s) update to %s", op, opMode(op), opMode(newOp));
                    key.interestOps(newOp);
                }
                else {
                    try {
                        cop.ch.register(agent.selector, cop.op);
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

    public void closeTimeoutSockets() throws IOException {
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

        for (SelectableChannel ch : channels.keySet()) {
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
            agent.acceptHandler.onClosed();

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
