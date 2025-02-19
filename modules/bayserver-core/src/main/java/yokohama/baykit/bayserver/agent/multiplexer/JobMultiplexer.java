package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.DatagramChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * The purpose of JobMultiplexer is handling sockets, pipes, or files by thread/fiber/goroutine.
 */
public class JobMultiplexer extends JobMultiplexerBase {

    public JobMultiplexer(GrandAgent agent, boolean anchorable) {
        super(agent, anchorable);
    }

    public String toString() {
        return "JobMpx[" + agent + "]";
    }


    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    @Override
    public void reqAccept(Rudder rd) {
        BayLog.debug("%s reqAccept isShutdown=%b", agent, agent.aborted);
        if (agent.aborted) {
            return;
        }

        ServerSocketChannel sch = (ServerSocketChannel) ChannelRudder.getChannel(rd);
        RudderState st = findRudderStateByKey(sch);
        int id = st.id;

        new Thread(() -> {
            try {
                if (agent.aborted) {
                    return;
                }

                SocketChannel ch;
                try {
                    ch = sch.accept();
                } catch (IOException e) {
                    agent.sendErrorLetter(id, rd, this, e, true);
                    return;
                }

                BayLog.debug("%s Accepted ch=%s", agent, ch);
                if(agent.aborted) {
                    BayLog.error("%s Agent is not alive (close)", agent);
                    try {
                        ch.close();
                    }
                    catch(IOException e) {
                    }
                }
                else {
                    agent.sendAcceptedLetter(id, rd, this, new SocketChannelRudder(ch), true);
                }

            } catch(Throwable e) {
                BayLog.fatal(e);
                agent.shutdown();
            }
        }).start();
    }

    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if(rd == null)
            throw new NullPointerException();
        BayLog.debug("%s reqConnect addr=%s rd=%s", agent, addr, rd);

        new Thread(() -> {

            RudderState st = getRudderState(rd);
            if (st == null) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }
            int id = st.id;

            try {
                SocketChannel ch = (SocketChannel)ChannelRudder.getChannel(rd);
                ch.connect(addr);
            } catch (IOException e) {
                agent.sendErrorLetter(id, rd, this, e, true);
                return;
            }

            agent.sendConnectedLetter(id, rd, this, true);

        }).start();

        RudderState st = getRudderState(rd);
        st.access();
    }


    public void reqRead(Rudder rd) {
        if(rd == null)
            throw new NullPointerException();

        RudderState st = getRudderState(rd);
        if(st == null)
            return;

        BayLog.debug("%s reqRead rd=%s state=%s reading=%b", agent, st.rudder, st, st.reading);
        boolean needRead = false;
        synchronized (st.reading) {
            if (!st.reading[0]) {
                needRead = true;
                st.reading[0] = true;
            }
        }

        if(needRead)
            nextRead(st);

        st.access();
    }

    public synchronized void reqWrite(Rudder rd, ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener)
        throws IOException {
        if(rd == null)
            throw new NullPointerException();

        RudderState state = getRudderState(rd);
        BayLog.debug("%s reqWrite chState=%s tag=%s len=%d", agent, state, tag, buf.remaining());
        if(state == null) {
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
            nextWrite(state);
        }

        state.access();
    }

    @Override
    public void reqEnd(Rudder rd) {
        RudderState state = getRudderState(rd);
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
        RudderState state = getRudderState(rd);
        if (state == null) {
            BayLog.debug("%s Rudder state not found: rd=%s", agent, rd);
            return;
        }
        int id = state.id;

        new Thread(() -> {
            try {
                RudderState st = getRudderState(rd);
                if (st == null) {
                    // channel is already closed
                    BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                    return;
                }

                closeRudder(rd);
                agent.sendClosedLetter(id, rd, this, true);
            } catch(Throwable e) {
                BayLog.fatal(e);
                agent.shutdown();
            }
        }).start();

        state.access();
    }

    @Override
    public void cancelRead(RudderState st) {

    }

    @Override
    public void cancelWrite(RudderState st) {

    }

    @Override
    public void nextAccept(RudderState state) {
        reqAccept(state.rudder);
    }

    @Override
    public void nextRead(RudderState st) {

        int id = st.id;
        new Thread(() -> {
            int n;
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
                        n = st.readBuf.limit();
                    }
                }
                else {
                    //st.readBuf.clear();
                    BayLog.debug("%s Try to Read (rd=%s) (buf=%s)", agent, st.rudder, st.readBuf);
                    n = st.rudder.read(st.readBuf);
                    if (n > 0)
                        st.readBuf.flip();
                }

                if(getRudderState(st.rudder) == null) {
                    BayLog.debug("%s Rudder is already closed: %s", this, st.rudder);
                }
                else {
                    agent.sendReadLetter(id, st.rudder, this, n, sender, true);
                }

            } catch (AsynchronousCloseException e) {
                BayLog.debug("%s Closed by another thread: %s (%s)", this, st.rudder, e);
                // Do not do next action
            } catch (IOException e) {
                agent.sendErrorLetter(id, st.rudder, this, e, true);
            }

        }).start();
    }

    @Override
    public void nextWrite(RudderState st) {

        int id = st.id;
        new Thread(() -> {
            if (st == null) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, st.rudder);
                return;
            }

            WriteUnit u = st.writeQueue.get(0);
            BayLog.debug("%s Try to write: pkt=%s buflen=%d", this, u.tag, u.buf.limit());

            int n = 0;
            try {
                if(u.buf.limit() > 0) {
                    if (st.rudder instanceof DatagramChannelRudder) {
                        n = DatagramChannelRudder.getDataGramChannel(st.rudder).send(u.buf, u.adr);
                    }
                    else {
                        n = st.rudder.write(u.buf);
                    }
                }
            } catch (IOException e) {
                agent.sendErrorLetter(id, st.rudder, this, e, true);
                return;
            }
            agent.sendWroteLetter(id, st.rudder, this, n, true);

        }).start();

    }

    @Override
    public boolean isNonBlocking() {
        return false;
    }

    @Override
    public boolean useAsyncAPI() {
        return false;
    }

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////


}
