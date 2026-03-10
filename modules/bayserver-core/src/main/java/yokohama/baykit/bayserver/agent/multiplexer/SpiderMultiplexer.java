package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.Recipient;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.WriteUnit;
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

    private final boolean anchorable;

    private Selector selector;

    final ArrayList<ChannelRudder> ruddersToRegister = new ArrayList<>(16384);

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
        try {
            SelectionKey key = ((ServerSocketChannel)ChannelRudder.getChannel(rd)).register(selector, SelectionKey.OP_ACCEPT);
            key.attach(rd);
        }
        catch(ClosedChannelException e) {
            BayLog.error(e);
        }
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

        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqRead chState=%s", agent, st);
        addOperation(rd, OP_READ);

        if(st == null)
            return;

        st.access();
    }

    @Override
    public void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToWrite");
        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s tag=%s len=%d", agent, st, tag, buf.remaining());
        if(st == null) {
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
    public void reqTransfer(Rudder rd, Rudder fileRd, int ofs, int len, DataConsumeListener listener) {
        if(rd == null)
            throw new NullPointerException();

        //BayLog.debug("askToWrite");
        RudderState st = getRudderState(rd);
        BayLog.debug("%s reqTransfer chState=%s ofs=%d len=%d", agent, st, ofs, len);
        if(st == null) {
            BayLog.warn("%s Channel is closed: %s", agent, rd);
            listener.dataConsumed();
            return;
        }

        WriteUnit unt = new WriteUnit(fileRd, ofs, len, listener);
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
        BayLog.debug("%s reqClose rd=%s", agent, rd);
        if(rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        if(st == null) {
            BayLog.warn("%s RudderState not found: %s", agent, rd);
            return;
        }

        closeRudder(rd);
        agent.sendClosedLetter(st.id, rd, this, false);
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
        SelectionKey key = st.selectionKey;
        if(key == null)
            return;

        key.cancel();
        st.selectionKey = null;
    }

    @Override
    public void cancelWrite(RudderState st) {
        if(st.rudder.closed())
            return;

        SelectionKey key = st.selectionKey;
        if(key == null)
            return;

        // Write OP Off
        try {
            int op = key.interestOps();
            if((op & OP_WRITE) == 0) {
                BayLog.debug( "%s do nothing: %s", agent, key);
            }
            else {
                int newOp = op & ~OP_WRITE;
                if (newOp != OP_READ) {
                    key.cancel();
                    st.selectionKey = null;
                }
                else
                    key.interestOps(newOp);
            }
        }
        catch(CancelledKeyException e) {
            BayLog.warn( "%s key cancelled: %s", agent, key);
        }
    }

    @Override
    public void nextAccept(RudderState state) {

    }

    @Override
    public void nextRead(RudderState st) {
        SelectionKey key = st.selectionKey;
        try {
            addOperation(st.rudder, OP_READ);
            //key.interestOps(key.interestOps() | OP_READ);
        }
        catch(CancelledKeyException e) {
            BayLog.error(e, "%s key cancelled: %s", agent, key);
        }
    }

    @Override
    public void nextWrite(RudderState st) {
        SelectionKey key = st.selectionKey;
        try {
            addOperation(st.rudder, OP_WRITE);
            //key.interestOps(key.interestOps() | OP_WRITE);
        }
        catch(CancelledKeyException e) {
            BayLog.error(e, "%s key cancelled: %s", agent, key);
        }
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
            reqAccept(pair.a);
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

    private void addOperation(Rudder rd, int op) {

        ChannelRudder crd = (ChannelRudder)rd;

        boolean firstRegister = ruddersToRegister.size() == 0;
        if(crd.inDirtyList){
            crd.pendingOps |= op;
            // BayLog.debug("%s Update operation: %d(%s) rd=%s", agent, cop.op, opMode(cop.op), cop.rudder);
        }
        else {
            //BayLog.debug("%s Add operation: %d(%s) rd=%s", agent, op, opMode(op), rd);
            crd.pendingOps = op;
            ruddersToRegister.add(crd);
            crd.inDirtyList = true;
        }

        //BayLog.trace("%s wakeup", agent);
        if(firstRegister)
            selector.wakeup();
    }

    private int registerChannelOps() {
        if(ruddersToRegister.isEmpty())
            return 0;

        // register channels to selector
        int nch = ruddersToRegister.size();
        for (int i = 0; i < nch; i++) {
            ChannelRudder rd = ruddersToRegister.get(i);
            RudderState st = getRudderState(rd);
            if (st == null) {
                BayLog.debug("%s cannot register rudder: (rudder is closed)");
                continue;
            }

            SelectableChannel ch =  (SelectableChannel)ChannelRudder.getChannel(rd);
            //BayLog.debug("%s register chState=%s register op=%d(%s) ch=%s", agent, st, rd.op, opMode(cop.op), ch);
            SelectionKey key = st.selectionKey;
            if(key != null) {
                try {
                    int op = key.interestOps();
                    int newOp = op | rd.pendingOps;
                    if(newOp != op) {
                        //BayLog.debug("Already registered op=%d(%s) update to %s", op, opMode(op), opMode(newOp));
                        key.interestOps(newOp);
                    }
                } catch (CancelledKeyException e) {
                    BayLog.debug(e, "%s Cannot modify operation (Channel is canceled): %s ch=%s", agent, st, rd);
                }
            }
            else {
                try {
                    BayLog.debug("%s register rudder: ch=%s ops=%d", this, ch, rd.pendingOps);
                    key = ch.register(selector, rd.pendingOps);
                    key.attach(rd);
                    st.selectionKey = key;
                } catch (ClosedChannelException e) {
                    //BayLog.debug(e, "%s Cannot register operation (Channel is closed): %s ch=%s op=%d(%s) close=%b",
                    //        agent, st, cop.rudder, cop.op, opMode(cop.op), cop.close);
                }
            }
            rd.inDirtyList = false;
            rd.pendingOps = 0;
        }
        ruddersToRegister.clear();
        return nch;
    }


    private void handleChannel(SelectionKey key) {

        RudderState st;
        // ready for read
        SelectableChannel ch = key.channel();
        Rudder rd = (Rudder)key.attachment();
        st = getRudderState(rd);
        if (st == null) {
            BayLog.warn("%s Channel state is not registered", agent);
            key.cancel();
            st.selectionKey = null;
            return;
        }

        BayLog.debug("%s handleChannel st=%s acceptable=%b readable=%b writable=%b connectable=%b",
                        agent, st, key.isAcceptable(), key.isReadable(), key.isWritable(), key.isConnectable());

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
        int id = st.id;
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
                    agent.sendAcceptedLetter(id, serverRd, this, clientRd, false);
                }

            } catch (IOException e) {
                agent.sendErrorLetter(id, serverRd, this, e, false);
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
            agent.sendErrorLetter(st.id, st.rudder, this, e, false);
            return;
        }

        agent.sendConnectedLetter(st.id, st.rudder, this,false);
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
            agent.sendErrorLetter(st.id, st.rudder, this, e, false);
            return;

        }
        agent.sendReadLetter(st.id, st.rudder, this, c, sender, false);
    }

    private void onWritable(RudderState st) {
        try {
            if(st.writeQueue.isEmpty()) {
                BayLog.debug("%s No data to write", this);
                cancelWrite(st);
                return;
            }

            int i;
            for (i = 0; i < st.writeQueue.size(); i++) {
                WriteUnit wUnit = st.writeQueue.get(i);

                BayLog.debug("%s Try to write: pkt=%s pos=%d len=%d adr=%s",
                        this, wUnit.tag, wUnit.position(), wUnit.remaining(), wUnit.adr);
                //BayLog.debug(this + " " + new String(wUnit.buf.array(), 0, wUnit.buf.limit()));

                int tryLen = wUnit.remaining();
                int n;
                if (wUnit.skipFormalities()) {
                    n = (int)((FileChannel)ChannelRudder.getChannel(wUnit.file)).transferTo(
                            wUnit.position(),
                            tryLen,
                            (WritableByteChannel) ChannelRudder.getChannel(st.rudder));

                    BayLog.debug("%s Wrote %d/%d bytes", this, n, tryLen);
                    wUnit.forward(n);
                }
                else if (st.rudder instanceof DatagramChannelRudder) {
                    n = DatagramChannelRudder.getDataGramChannel(st.rudder).send(wUnit.buf, wUnit.adr);
                }
                else {
                    n = st.rudder.write(wUnit.buf);
                }

                agent.sendWroteLetter(st.id, st.rudder, this, n, false);
                if (n < tryLen) {
                    BayLog.debug("%s Wrote %d bytes (Data remains)", this, n);
                    break;
                }
            }
        }
        catch(IOException e) {
            agent.sendErrorLetter(st.id, st.rudder, this, e, false);
        }
    }

    private void onCloseReq(RudderState st) {
        BayLog.debug("%s onCloseReq: rd=%s", this, st.rudder);
        st.multiplexer.closeRudder(st.rudder);
        agent.sendClosedLetter(st.id, st.rudder, this, false);
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
