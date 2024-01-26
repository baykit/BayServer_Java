package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.TimerHandler;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
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

        synchronized (state.reading) {
            if (!state.reading[0]) {
                new Thread(() -> {
                    RudderState st = findRudderState(rd);
                    if (st == null || st.closing) {
                        // channel is already closed
                        BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                        return;
                    }

                    NextSocketAction nextAct;
                    try {
                        int n = rd.read(st.readBuf);
                        if (n == 0) {
                            nextAct = st.listener.notifyEof();
                        }
                        else {
                            st.readBuf.flip();
                            nextAct = st.listener.notifyRead(st.readBuf, null);
                        }

                    } catch (IOException e) {
                        st.listener.notifyError(e);
                        nextAct = NextSocketAction.Close;
                    } catch (Throwable e) {
                        st.listener.notifyError(e);
                        agent.reqShutdown();
                        nextAct = NextSocketAction.Close;
                    }

                    nextAction(st, nextAct, true);
                }).start();
                state.reading[0] = true;
            }
        }


        state.access();
    }

    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = findRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s len=%d", agent, state, buf.remaining());
        if(state == null || !state.valid) {
            throw new IOException("Invalid rudder");
        }
        WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
        synchronized (state.writeQueue) {
            state.writeQueue.add(unt);
        }
        state.access();



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
                    BayLog.debug("%s Try to write: pkt=%s buflen=%d valid=%b", this, unit.tag, unit.buf.limit(), st.valid);
                    //BayLog.debug("Data: %s", new String(unit.buf.array(), unit.buf.position(), unit.buf.limit() - unit.buf.position()));

                    int n = 0;
                    if(st.valid && unit.buf.limit() > 0) {
                        n = rd.write(unit.buf);
                        BayLog.debug("wrote %d bytes", n);
                    }

                    if (n != unit.buf.limit()) {
                        throw new IOException("Could not write enough data");
                    }
                    st.writeQueue.remove(0);
                }

                unit.done();

            } catch (IOException e) {
                st.listener.notifyError(e);
            }

        }).start();

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

        BayLog.debug("%s askToClose rd=%s", agent, rd);
        RudderState state = findRudderState(rd);
        if (state == null) {
            BayLog.debug("%s Rudder state not found: rd=%s", agent, rd);
            return;
        }

        new Thread(() -> {
            try {
                rd.close();
            }
            catch(IOException e) {
                BayLog.error(e);
            }
        }).start();

        state.access();
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

    private void nextAction(RudderState st, NextSocketAction act, boolean reading) {
        switch(act) {
            case Continue:
                break;

            case Read:
                reqRead(st.rudder);
                break;

            case Write:
                if(reading)
                    cancelRead(st);
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

    private void cancelRead(RudderState st) {
        synchronized (st.reading) {
            BayLog.debug("%s Reading off %s", agent, st.rudder);
            st.reading[0] = false;
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

                    try {
                        p.checkAdmitted(ch);
                    } catch (HttpException e) {
                        BayLog.error(e);
                        try {
                            ch.close();
                        } catch (IOException ex) {
                        }
                        return;
                    }

                    Rudder clientRd = new ChannelRudder(ch);

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
}
