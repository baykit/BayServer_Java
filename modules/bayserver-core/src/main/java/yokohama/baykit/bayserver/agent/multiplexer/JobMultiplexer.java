package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
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

    ////////////////////////////////////////////
    // Implements Multiplexer
    ////////////////////////////////////////////

    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        if(rd == null)
            throw new NullPointerException();
        BayLog.debug("%s reqConnect addr=%s rd=%s", agent, addr, rd);

        new Thread(() -> {

            RudderState st = getRudderState(rd);
            if (st == null || st.closed) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                return;
            }

            try {
                SocketChannel ch = (SocketChannel)ChannelRudder.getChannel(rd);
                ch.connect(addr);
            } catch (IOException e) {
                agent.sendConnectedLetter(st, e, true);
                return;
            }

            agent.sendConnectedLetter(st, null, true);

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

        BayLog.debug("%s reqRead rd=%s state=%s", agent, st.rudder, st);
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

        new Thread(() -> {
            try {
                RudderState st = getRudderState(rd);
                if (st == null) {
                    // channel is already closed
                    BayLog.debug("%s Rudder is already closed: rd=%s", agent, rd);
                    return;
                }

                closeRudder(st);
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
    public boolean useAsyncAPI() {
        return false;
    }

    @Override
    public void onBusy() {

    }

    ////////////////////////////////////////////
    // Implements JobMultiplexerBase
    ////////////////////////////////////////////

    @Override
    protected void reqAccept(Rudder rd) {
        BayLog.debug("%s AcceptHandler:reqAccept isShutdown=%b", agent, agent.aborted);
        if (agent.aborted) {
            return;
        }

        ServerSocketChannel sch = (ServerSocketChannel) ChannelRudder.getChannel(rd);
        RudderState st = findRudderStateByKey(sch);

        new Thread(() -> {
            try {
                if (agent.aborted) {
                    return;
                }

                SocketChannel ch;
                try {
                    ch = sch.accept();
                } catch (IOException e) {
                    agent.sendAcceptedLetter(st, null, e, true);
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
                    agent.sendAcceptedLetter(st, new SocketChannelRudder(ch), null, true);
                }

            } catch(Throwable e) {
                BayLog.fatal(e);
                agent.shutdown();
            }
        }).start();
    }

    @Override
    public void nextRead(RudderState st) {

        new Thread(() -> {
            if (st.closed) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, st.rudder);
                return;
            }

            int n;
            try {
                st.readBuf.clear();
                BayLog.debug("%s Try to Read (rd=%s) (buf=%s) timeout=%d", agent, st.rudder, st.readBuf, agent.timeoutSec);
                n = st.rudder.read(st.readBuf);
                if(n > 0)
                    st.readBuf.flip();
            } catch (AsynchronousCloseException e) {
                BayLog.debug("%s Closed by another thread: %s (%s)", this, st.rudder, e);
                return; // Do not do next action
            } catch (IOException e) {
                agent.sendReadLetter(st, -1, e, true);
                return;
            }

            agent.sendReadLetter(st, n, null, true);
        }).start();
    }

    @Override
    public void nextWrite(RudderState st) {

        new Thread(() -> {
            if (st == null || st.closed) {
                // channel is already closed
                BayLog.debug("%s Rudder is already closed: rd=%s", agent, st.rudder);
                return;
            }

            WriteUnit u = st.writeQueue.get(0);
            BayLog.debug("%s Try to write: pkt=%s buflen=%d closed=%b", this, u.tag, u.buf.limit(), st.closed);

            int n = 0;
            try {
                if(!st.closed && u.buf.limit() > 0) {
                    n = st.rudder.write(u.buf);
                }
            } catch (IOException e) {
                agent.sendWroteLetter(st, -1, e, true);
                return;
            }
            agent.sendWroteLetter(st, n, null, true);

        }).start();

    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////


}
