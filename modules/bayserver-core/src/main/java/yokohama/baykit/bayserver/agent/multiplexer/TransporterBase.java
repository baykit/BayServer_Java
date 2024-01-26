package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.UpgradeException;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public abstract class TransporterBase implements Transporter, Reusable {
    
    protected static class WaitReadableException extends IOException {

    }

    protected final boolean serverMode;
    protected final boolean traceSSL;
    protected boolean needHandshake = true;
    protected boolean writeOnly = false;
    protected InetSocketAddress[] tmpAddress = new InetSocketAddress[1];

    /////////////////////////////////////////////////////////////////////////////////
    // abstract methods
    /////////////////////////////////////////////////////////////////////////////////

    protected abstract boolean handshake(Rudder rd, DataListener lis, boolean readable) throws IOException;
    protected abstract ByteBuffer readNonBlock(Rudder rd, InetSocketAddress[] adr) throws IOException;
    protected abstract boolean writeNonBlock(Rudder rd, InetSocketAddress adr, ByteBuffer buf) throws IOException;
    protected abstract boolean secure();


    public TransporterBase(boolean serverMode, boolean traceSSL, boolean writeOnly) {
        this.serverMode = serverMode;
        this.traceSSL = traceSSL;
        this.writeOnly = writeOnly;
    }

    public TransporterBase(boolean serverMode, boolean traceSSL) {
        this(serverMode, traceSSL, false);
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements SelectListener
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction onConnectable(RudderState st) throws IOException {
        BayLog.trace("%s onConnectable", this);

        try {
            ((SocketChannel)ChannelRudder.getChannel(st.rudder)).finishConnect();
        }
        catch(IOException e) {
            BayLog.error("Connect failed: %s", e);
            return NextSocketAction.Close;
        }

        return st.listener.notifyConnect();
    }

    @Override
    public NextSocketAction onReadable(RudderState st) throws IOException {
        BayLog.trace("%s onReadable", this);

        if(needHandshake) {
            boolean remain;
            try {
                remain = handshake(st.rudder, st.listener, true);
            }
            catch(WaitReadableException e) {
                return NextSocketAction.Continue;
            }


            // Handshake is done
            if(!remain)  {
                // no ramaining process (maybe next process is writing)
                return NextSocketAction.Continue;
            }
        }

        ByteBuffer buf = null;
        try {
            buf = readNonBlock(st.rudder, tmpAddress);
        }
        catch(EOFException e) {
            BayLog.debug("%s EOF (ignore): %s", this, e);
        }

        if(buf == null) {
            // Does not throw IOException
            return st.listener.notifyEof();
        }
        else {
            try {
                return st.listener.notifyRead(buf, tmpAddress[0]);
            }
            catch(UpgradeException e) {
                BayLog.debug("%s Protocol upgrade", st.listener);
                buf.rewind();
                return st.listener.notifyRead(buf, tmpAddress[0]);
            }
            catch (ProtocolException e) {
                boolean close = st.listener.notifyProtocolError(e);
                if(!close && serverMode)
                    return NextSocketAction.Continue;
                else
                    return NextSocketAction.Close;
            }
            catch (IOException e) {
                // IOException which occur in notifyRead must be distinguished from
                // IOException which occur in handshake or readNonBlock.
                onError(st, e);
                return NextSocketAction.Close;
            }
        }
    }

    @Override
    public NextSocketAction onWritable(RudderState st) throws IOException {
        BayLog.trace("%s onWritable", this);

        if(needHandshake) {
            try {
                handshake(st.rudder, st.listener, false);
            }
            catch(WaitReadableException e) {
                return NextSocketAction.Read;
            }
        }

        while(!st.writeQueue.isEmpty()) {
            WriteUnit wUnit = st.writeQueue.get(0);

            BayLog.debug("%s Try to write: pkt=%s pos=%d len=%d chValid=%b adr=%s", this, wUnit.tag, wUnit.buf.position(), wUnit.buf.limit(), st.valid, wUnit.adr);
            //BayLog.debug(this + " " + new String(wUnit.buf.array(), 0, wUnit.buf.limit()));

            if (st.valid && wUnit.buf.hasRemaining()) {
                if (!writeNonBlock(st.rudder, wUnit.adr, wUnit.buf)) {
                    // Data remains
                    BayLog.debug("%s data remains", this);
                    break;
                }
            }

            synchronized (this) {
                st.writeQueue.remove(0);
            }

            // packet send complete
            wUnit.done();
        }

        NextSocketAction state;
        if(st.writeQueue.isEmpty()) {
            if(st.finale) {
                BayLog.debug("%s finale return Close", this);
                state = NextSocketAction.Close;
            }
            else if(writeOnly) {
                state = NextSocketAction.Suspend;
            }
            else {
                state = NextSocketAction.Read; // will be handled as "Write Off"
            }
        }
        else
            state = NextSocketAction.Continue;
        return state;
    }

    @Override
    public void onError(RudderState st, Throwable e) {
        //BayLog.trace("%s onError: %s", this, e);
        st.listener.notifyError(e);
    }

    @Override
    public void onClosed(RudderState st) {
        st.listener.notifyClose();
    }

    @Override
    public boolean checkTimeout(RudderState st, int durationSec) {
        return st.listener.checkTimeout(durationSec);
    }
}
