package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.Recipient;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.DatagramChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Pair;

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
 * The purpose of SpiderMultiplexer is to monitor sockets, pipes, or files through the select/epoll/kqueue API.
 */
public class SpiderMultiplexer extends MultiplexerBase implements TimerHandler, Multiplexer, Recipient {

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

    private Selector selector;

    final ArrayList<ChannelOperation> operations = new ArrayList<>();

    public SpiderMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        try {
            this.selector = Selector.open();
        }
        catch(IOException e) {
            BayLog.fatal(e);
            System.exit(1);
        }

        agent.addTimerHandler(this);
    }

    public String toString() {
        return "SpiderMpx[" + agent + "]";
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqAccept(Rudder rd) {
        throw new Sink();
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState chState = getRudderState(rd);
        BayLog.debug("%s reqConnect addr=%s rd=%s chState=%s", agent, addr, rd, chState);

        rd.setNonBlocking();
        ((SocketChannel)ChannelRudder.getChannel(rd)).connect(addr);

        if(!(addr instanceof InetSocketAddress)) {
            // Unix domain socket does not support connect operation
            onConnectable(chState);
        }
        else {
            addOperation(rd, OP_CONNECT);
        }
    }

    @Override
    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToRead");
        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqRead chState=%s", agent, st);
        addOperation(rd, OP_READ);

        if(st == null)
            return;

        st.access();
    }

    @Override
    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToWrite");
        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s tag=%s len=%d", agent, st, tag, buf.remaining());
        if(st == null || st.closed) {
            BayLog.warn("%s Channel is closed: %s", agent, rd);
            listener.dataConsumed();
            return;
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
        RudderState st = getRudderState(rd);
        if(st == null)
            return;

        st.end();
        st.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqClose chState=%s", agent, st);
        addOperation(rd, OP_WRITE, true);

        if(st == null)
            return;

        st.access();
    }

    @Override
    public void shutdown() {
        selector.wakeup();
    }

    @Override
    public boolean isNonBlocking() {
        return true;
    }

    @Override
    public boolean useAsyncAPI() {
        return false;
    }

    @Override
    public void cancelRead(RudderState st) {
        st.selectionKey.cancel();
    }

    @Override
    public void cancelWrite(RudderState st) {
        SelectionKey key = st.selectionKey;
        // Write OP Off
        int op = key.interestOps() & ~OP_WRITE;
        if (op != OP_READ)
            key.cancel();
        else
            key.interestOps(op);
    }

    @Override
    public void nextAccept(RudderState state) {

    }

    @Override
    public void nextRead(RudderState st) {
        SelectionKey key = st.selectionKey;
        key.interestOps(key.interestOps() | OP_READ);
    }

    @Override
    public void nextWrite(RudderState st) {
        SelectionKey key = st.selectionKey;
        key.interestOps(key.interestOps() | OP_WRITE);
    }

    @Override
    public synchronized void onBusy() {
        BayLog.debug("%s onBusy", agent);
        for(Pair<Rudder, Port> pair: BayServer.anchorablePorts) {
            SelectionKey key = ((ServerSocketChannel)ChannelRudder.getChannel(pair.a)).keyFor(selector);
            if(key != null)
                key.cancel();
        }
    }

    @Override
    public synchronized void onFree() {
        BayLog.debug("%s onFree aborted=%s", agent, agent.aborted);
        if(agent.aborted)
            return;

        for(Pair<Rudder, Port> pair: BayServer.anchorablePorts) {
            try {
                ((ServerSocketChannel)ChannelRudder.getChannel(pair.a)).register(selector, SelectionKey.OP_ACCEPT);
            }
            catch(ClosedChannelException e) {
                BayLog.error(e);
            }
        }
    }


    ////////////////////////////////////////////
    // Implements TimerHandler
    ////////////////////////////////////////////
    @Override
    public void onTimer() {
        closeTimeoutSockets();
    }


    ////////////////////////////////////////////
    // Implements Recipient
    ////////////////////////////////////////////
    @Override
    public boolean receive(boolean wait) throws IOException{
        int count;
        if (!wait) {
            count = selector.selectNow();
        }
        else {
            count = selector.select(GrandAgent.SELECT_TIMEOUT_SEC * 1000L);
        }

        //BayLog.debug(this + " select count=" + count);
        registerChannelOps();

        Set<SelectionKey> selKeys = selector.selectedKeys();

        for(Iterator<SelectionKey> it = selKeys.iterator(); it.hasNext(); ) {
            SelectionKey key = it.next();
            it.remove();
            handleChannel(key);
        }

        return count > 0;
    }

    @Override
    public void wakeup() {
        selector.wakeup();
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
                RudderState st = getRudderState(cop.rudder);
                if (st == null) {
                    BayLog.debug("%s cannot register rudder: (rudder is closed)");
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

        RudderState st;
        // ready for read
        SelectableChannel ch = key.channel();
        st = findRudderStateByKey(ch);
        if (st == null) {
            BayLog.warn("%s Channel state is not registered", agent);
            key.cancel();
            return;
        }

        BayLog.debug("%s handleChannel st=%s acceptable=%b readable=%b writable=%b connectable=%b",
                        agent, st, key.isAcceptable(), key.isReadable(), key.isWritable(), key.isConnectable());
        st.selectionKey = key;

        try {
            if (st.closing) {
                onCloseReq(st);
            }
            else if (key.isAcceptable()) {
                onAcceptable(st);
            }
            else if (key.isConnectable()) {
                BayLog.debug("%s chState=%s socket connectable", agent, st);

                // Cancel connect operation
                int op = key.interestOps() & ~OP_CONNECT;
                key.interestOps(op);

                onConnectable(st);
            }
            else if (key.isReadable()) {
                BayLog.trace("%s chState=%s socket readable", agent, st);
                onReadable(st);
            }
            else if (key.isWritable()) {
                BayLog.trace("%s chState=%s socket writable", agent, st);
                onWritable(st);
            }
        } catch (Throwable e) {
            if(e instanceof Sink){
                BayLog.error("%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw (Sink)e;
            }
            else {
                BayLog.error(e, "%s Unhandled error error: %s (skt=%s)", agent, e, ch);
                throw new Sink("Unhandled error: %s", e);
            }
            // Cannot handle Exception any more
        }

        st.access();
    }

    private void onAcceptable(RudderState st) {

        Rudder serverRd = st.rudder;
        ServerSocketChannel sch = (ServerSocketChannel) SocketChannelRudder.getChannel(serverRd);

        //BayLog.debug(this + " onAcceptable");
        while(true) {
            SocketChannel ch = null;
            try {
                ch = sch.accept();

                if (ch == null) {
                    // Another agent caught client socket
                    return;
                }

                BayLog.debug("%s Accepted ch=%s", agent, ch);
                if(agent.aborted) {
                    throw new IOException("Agent is not alive");
                }
                else {
                    SocketChannelRudder clientRd = new SocketChannelRudder(ch);
                    clientRd.setNonBlocking();
                    agent.sendAcceptedLetter(st, clientRd, false);
                }

            } catch (IOException e) {
                agent.sendErrorLetter(st, e, false);
                if(ch != null) {
                    try { ch.close(); } catch (IOException ee) {}
                }
            }
        }

    }

    private void onConnectable(RudderState st) {
        BayLog.trace("%s onConnectable", this);

        try {
            ((SocketChannel)ChannelRudder.getChannel(st.rudder)).finishConnect();
        }
        catch(IOException e) {
            BayLog.error("%s Connect failed: %s", this, e);
            agent.sendErrorLetter(st, e, false);
            return;
        }

        agent.sendConnectedLetter(st,false);
    }

    private void onReadable(RudderState st) {
        // read data
        //st.readBuf.clear();

        int c = 0;
        InetSocketAddress sender = null;
        try {
            if(st.rudder instanceof DatagramChannelRudder) {
                // UDP
                sender = (InetSocketAddress) DatagramChannelRudder.getDataGramChannel(st.rudder).receive(st.readBuf);
                if (sender == null) {
                    BayLog.trace("%s Empty packet data (Maybe another agent received data)", this);
                    return;
                }
                else {
                    st.readBuf.flip();
                    c = st.readBuf.limit();
                }
            }
            else {
                // TCP
                c = st.rudder.read(st.readBuf);
                if (c == -1)
                    st.readBuf.limit(0);
                else
                    st.readBuf.flip();
                BayLog.debug("%s read %d bytes", this, st.readBuf.limit());
            }
        }
        catch(IOException e) {
            agent.sendErrorLetter(st, e, false);
            return;

        }
        agent.sendReadLetter(st, c, sender, false);
    }

    private void onWritable(RudderState st) {
        try {
            if(st.writeQueue.isEmpty())
                throw new IOException(agent + " No data to write");

            int i;
            for(i = 0; i < st.writeQueue.size(); i++) {
                WriteUnit wUnit = st.writeQueue.get(i);

                BayLog.debug("%s Try to write: pkt=%s pos=%d len=%d closed=%b adr=%s",
                        this, wUnit.tag, wUnit.buf.position(), wUnit.buf.remaining(), st.closed, wUnit.adr);
                //BayLog.debug(this + " " + new String(wUnit.buf.array(), 0, wUnit.buf.limit()));

                int remain = wUnit.buf.remaining();
                int n;
                if (st.rudder instanceof DatagramChannelRudder) {
                    n = DatagramChannelRudder.getDataGramChannel(st.rudder).send(wUnit.buf, wUnit.adr);
                }
                else {
                    n = st.rudder.write(wUnit.buf);
                }

                agent.sendWroteLetter(st, n, false);
                if(n < remain) {
                    BayLog.debug("%s Wrote %d bytes (Data remains)", this, n);
                    break;
                }
            }
        }
        catch(IOException e) {
            agent.sendErrorLetter(st, e, false);
        }
    }

    private void onCloseReq(RudderState st) {
        BayLog.debug("%s onCloseReq: rd=%s", this, st.rudder);
        st.multiplexer.closeRudder(st.rudder);
        agent.sendClosedLetter(st, false);
    }


    private static String opMode(int mode) {
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

    private void doShutdown() {
        closeAll();
    }


}
