package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.CommandReceiver;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public class JobMultiplexer extends MultiplexerBase implements TimerHandler, Multiplexer {

    private final boolean anchorable;
    private CommandReceiver commandReceiver;


    public JobMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent);

        this.anchorable = anchorable;
        agent.addTimerHandler(this);
    }

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void start() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            reqAccept(rd);
        }
    }

    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if(rd == null)
            throw new NullPointerException();
        BayLog.debug("%s reqConnect addr=%s rd=%s", agent, addr, rd);

        new Thread(() -> {
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }


            NextSocketAction nextAct;
            try {
                SocketChannel ch = (SocketChannel)ChannelRudder.getChannel(rd);
                ch.connect(addr);

                if(!(addr instanceof InetSocketAddress)) {
                    // Unix domain socket does not support connect operation
                    NextSocketAction nextSocketAction = st.transporter.onConnectable(st);
                    if(nextSocketAction == NextSocketAction.Continue)
                        reqRead(rd);
                }
                else {
                    ch.finishConnect();
                    //addOperation(rd, OP_CONNECT);
                }
                nextAct = st.listener.notifyConnect();
            } catch (IOException e) {
                st.listener.notifyError(e);
                nextAct = NextSocketAction.Close;
            }

            nextAction(st, nextAct, false);
        }).start();

        RudderState st = findRudderState(rd);
        st.access();
    }


    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = findRudderState(rd);
        if(state == null)
            return;

        boolean needRead = false;
        synchronized (state.reading) {
            if (!state.reading[0]) {
                needRead = true;
                state.reading[0] = true;
            }
        }

        if(needRead)
            nextRead(rd);

        state.access();
    }

    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = findRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s len=%d", agent, state, buf.remaining());
        if(state == null || state.closed) {
            throw new IOException("Invalid rudder");
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (state.writeQueue) {
            state.writeQueue.add(unt);
        }
        state.access();

        boolean needWrite = false;
        synchronized (state.writing) {
            if (!state.writing[0]) {
                needWrite = true;
                state.writing[0] = true;
            }
        }

        if(needWrite) {
            nextWrite(state.rudder);
        }

        state.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState state = findRudderState(rd);
        if(state == null)
            return;

        state.end();
        state.access();
    }

    @Override
    public void reqClose(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        BayLog.debug("%s reqClose rd=%s", agent, rd);
        RudderState state = findRudderState(rd);
        if (state == null) {
            BayLog.debug("%s Rudder state not found: rd=%s", agent, rd);
            return;
        }

        new Thread(() -> {
            RudderState st = findRudderState(rd);
            if (st == null) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            closeRudder(st);
        }).start();

        state.access();
    }

    public void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh) {
        commandReceiver = new CommandReceiver(agent, readCh, writeCh);
        new Thread(() -> {
            while (!commandReceiver.closed) {
                commandReceiver.onPipeReadable();
            }
        }).start();
    }

    public void shutdown() {
        commandReceiver.end();
        closeAll();
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            BayLog.fatal(e);
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
    // Private methods
    ////////////////////////////////////////////

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

    private void reqAccept(Rudder rd) {
        BayLog.debug("%s AcceptHandler:reqAccept isShutdown=%b", agent, agent.aborted);
        if (agent.aborted) {
            return;
        }

        new Thread(() -> {
            if (agent.aborted) {
                return;
            }

            Port p = BayServer.anchorablePortMap.get(rd);

            while (true) {
                SocketChannel ch = null;
                try {
                    ch = ((ServerSocketChannel) ChannelRudder.getChannel(rd)).accept();
                    BayLog.debug("%s Accepted ch=%s", agent, ch);

                    SocketChannelRudder clientRd = new SocketChannelRudder(ch);

                    try {
                        p.checkAdmitted(clientRd);
                    } catch (HttpException e) {
                        BayLog.error(e);
                        try {
                            ch.close();
                        } catch (IOException ex) {
                        }
                        return;
                    }


                    DataListener lis = p.newDataListener(agent.agentId, clientRd);
                    Transporter tp = p.newTransporter(agent.agentId, clientRd);
                    RudderState st = new RudderState(clientRd, lis, tp);
                    agent.netMultiplexer.addState(clientRd, st);
                    agent.netMultiplexer.reqRead(clientRd);
                } catch (IOException e) {
                    BayLog.error(e);
                    if (ch != null) {
                        try {
                            ch.close();
                        } catch (IOException ex) {
                            BayLog.error(ex);
                        }
                    }
                }
            }
        }).start();
    }

    private void nextRead(Rudder rd) {
        new Thread(() -> {
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            NextSocketAction nextAct;
            try {
                st.readBuf.clear();
                BayLog.debug("%s readBuf %s", agent, st.readBuf);
                int n = rd.read(st.readBuf);
                BayLog.debug("%s read %d bytes", agent, n);
                if (n < 0) {
                    nextAct = st.listener.notifyEof();
                } else if (n == 0) {
                    // Continue
                    nextAct = NextSocketAction.Continue;
                } else {
                    st.readBuf.flip();
                    nextAct = st.listener.notifyRead(st.readBuf, null);
                }
                BayLog.debug("%s Next action %s", agent, nextAct);

            } catch (AsynchronousCloseException e) {
                BayLog.debug(e, "%s Closed by another thread: %s", this, st.rudder);
                nextAct = NextSocketAction.Close;
            } catch (IOException e) {
                st.listener.notifyError(e);
                nextAct = NextSocketAction.Close;
            } catch (Throwable e) {
                BayLog.fatal(e);
                agent.shutdown();
                nextAct = NextSocketAction.Close;
            }

            nextAction(st, nextAct, true);
        }).start();
    }

    private void nextWrite(Rudder rd) {
        new Thread(() -> {
            RudderState st = findRudderState(rd);
            if (st == null || st.closing) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            try {
                WriteUnit unit;
                synchronized (st.writeQueue) {
                    unit = st.writeQueue.get(0);
                    BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b", this, unit.tag, unit.buf.limit(), st.closed);
                    //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

                    int n = 0;
                    if(!st.closed && unit.buf.limit() > 0) {
                        n = rd.write(unit.buf);
                        //BayLog.debug("wrote %d bytes", n);
                    }

                    if (n != unit.buf.limit()) {
                        throw new IOException("Could not write enough data: " + n + "/" + unit.buf.limit());
                    }
                    st.writeQueue.remove(0);
                }

                unit.done();

            } catch (Throwable e) {
                if(st.closed) {
                    BayLog.debug(e, "Rudder is closed. Ignore error");
                }
                else {
                    st.listener.notifyError(e);
                    nextAction(st, NextSocketAction.Close, false);
                }
            }

            boolean writeMore = true;
            synchronized (st.writing) {
                if (st.writeQueue.isEmpty()) {
                    writeMore = false;
                    st.writing[0] = false;
                }
            }

            if(writeMore) {
                nextWrite(st.rudder);
            }

        }).start();

    }

    private void nextAction(RudderState st, NextSocketAction act, boolean reading) {
        try {
            switch (act) {
                case Continue:
                    if(reading)
                        nextRead(st.rudder);
                    break;

                case Read:
                    nextRead(st.rudder);
                    break;

                case Write:
                    if(reading)
                        cancelRead(st);
                    nextWrite(st.rudder);
                    break;

                case Close:
                    if(reading)
                        cancelRead(st);
                    closeRudder(st);
                    break;

                case Suspend:
                    if(reading)
                        cancelRead(st);
                    break;
            }
            st.access();
        }
        catch(Throwable e) {
            BayLog.fatal(e);
            agent.shutdown();
        }
    }

    private void cancelRead(RudderState st) {
        synchronized (st.reading) {
            BayLog.debug("%s Reading off %s", agent, st.rudder);
            st.reading[0] = false;
        }
    }

}
