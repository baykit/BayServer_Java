package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.NonBlockingHandler;
import yokohama.baykit.bayserver.agent.UpgradeException;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Postman;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.Valve;

import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public abstract class Transporter implements ChannelListener, Reusable, Postman, Valve {
    
    protected static class WaitReadableException extends IOException {

    }

    static class WriteUnit {
        final ByteBuffer buf;
        final InetSocketAddress adr;
        final Object tag;
        final DataConsumeListener listener;

        WriteUnit(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) {
            this.buf = buf;
            this.adr = adr;
            this.tag = tag;
            this.listener = listener;
        }

        void done() {
            if(listener != null)
                listener.dataConsumed();
        }
    }

    protected DataListener dataListener;
    protected final boolean serverMode;
    protected final boolean traceSSL;
    protected final boolean writeOnly;
    protected SelectableChannel ch;
    protected ArrayList<WriteUnit> writeQueue = new ArrayList<>();
    protected boolean finale;
    protected boolean initialized;
    protected boolean chValid = false;
    protected boolean needHandshake = true;
    NonBlockingHandler nonBlockingHandler;
    InetSocketAddress[] tmpAddress = new InetSocketAddress[1];

    /////////////////////////////////////////////////////////////////////////////////
    // abstract methods
    /////////////////////////////////////////////////////////////////////////////////

    protected abstract boolean handshake(boolean readable) throws IOException;
    protected abstract ByteBuffer readNonBlock(InetSocketAddress[] adr) throws IOException;
    protected abstract boolean writeNonBlock(ByteBuffer buf, InetSocketAddress adr) throws IOException;
    protected abstract boolean secure();


    public Transporter(boolean serverMode, boolean traceSSL, boolean writeOnly) {
        this.serverMode = serverMode;
        this.traceSSL = traceSSL;
        this.writeOnly = writeOnly;
    }

    public Transporter(boolean serverMode, boolean traceSSL) {
        this(serverMode, traceSSL, false);
    }

    public void init(NonBlockingHandler chHnd, SelectableChannel ch, DataListener lis) {

        if(initialized)
            throw new Sink(this + " This transporter is already in use by channel: " + ch);
        if(!writeQueue.isEmpty())
            throw new Sink();

        this.nonBlockingHandler = chHnd;
        this.dataListener = lis;
        this.ch = ch;
        setValid(true);
        this.initialized = true;
        nonBlockingHandler.addChannelListener(ch, this);
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements Reusable
    /////////////////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {

        // Check write queue
        if(!writeQueue.isEmpty())
            throw new Sink("Write queue is not empty");

        finale = false;
        initialized = false;
        ch = null;
        setValid(false);
        needHandshake = true;
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements Postman
    /////////////////////////////////////////////////////////////////////////////////

    public final void post(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        checkInitialized();

        BayLog.debug("%s post: %s len=%d", this, tag, buf.limit());

        if(!chValid) {
            throw new IOException("Invalid channel");
        }
        else {
            WriteUnit unt = new WriteUnit(buf, adr, tag, listener);
            synchronized (this) {
                writeQueue.add(unt);
            }
            BayLog.trace("%s sendBytes->askToWrite", this);
            nonBlockingHandler.askToWrite(ch);
        }
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements Valve
    /////////////////////////////////////////////////////////////////////////////////

    public void openValve() {
        BayLog.debug("%s open valve", this);
        nonBlockingHandler.askToRead(ch);
    }

    public void abort() {
        BayLog.debug("%s abort", this);
        nonBlockingHandler.askToClose(ch);
    }

    public boolean isZombie() {
        return ch != null && !chValid;
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements NonBlockingHandler.ChannelListener
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    public NextSocketAction onConnectable(Channel chkCh) throws IOException {
        checkChannel(chkCh);
        BayLog.trace("%s onConnectable", this);

        try {
            ((SocketChannel)chkCh).finishConnect();
        }
        catch(IOException e) {
            BayLog.error("Connect failed: %s", e);
            return NextSocketAction.Close;
        }
        return dataListener.notifyConnect();
    }

    @Override
    public final NextSocketAction onReadable(Channel ch) throws IOException {
        checkChannel(ch);
        BayLog.trace("%s onReadable", this);

        if(needHandshake) {
            boolean remain;
            try {
                remain = handshake(true);
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
            buf = readNonBlock(tmpAddress);
        }
        catch(EOFException e) {
            BayLog.debug("%s EOF (ignore): %s", this, e);
        }

        if(buf == null) {
            // Does not throw IOException
            return dataListener.notifyEof();
        }
        else {
            try {
                return dataListener.notifyRead(buf, tmpAddress[0]);
            }
            catch(UpgradeException e) {
                BayLog.debug("%s Protocol upgrade", dataListener);
                buf.rewind();
                return dataListener.notifyRead(buf, tmpAddress[0]);
            }
            catch (ProtocolException e) {
                boolean close = dataListener.notifyProtocolError(e);
                if(!close && serverMode)
                    return NextSocketAction.Continue;
                else
                    return NextSocketAction.Close;
            }
            catch (IOException e) {
                // IOException which occur in notifyRead must be distinguished from
                // IOException which occur in handshake or readNonBlock.
                onError(ch, e);
                return NextSocketAction.Close;
            }
        }
    }


    @Override
    public NextSocketAction onWritable(Channel ch) throws IOException {
        checkChannel(ch);
        BayLog.trace("%s onWritable", this);

        if(needHandshake) {
            try {
                handshake(false);
            }
            catch(WaitReadableException e) {
                return NextSocketAction.Read;
            }
        }

        while(!writeQueue.isEmpty()) {
            WriteUnit wUnit = writeQueue.get(0);

            BayLog.debug("%s Try to write: pkt=%s pos=%d len=%d chValid=%b adr=%s", this, wUnit.tag, wUnit.buf.position(), wUnit.buf.limit(), chValid, wUnit.adr);
            //BayLog.debug(this + " " + new String(wUnit.buf.array(), 0, wUnit.buf.limit()));

            if (chValid && wUnit.buf.hasRemaining()) {
                if (!writeNonBlock(wUnit.buf, wUnit.adr)) {
                    // Data remains
                    BayLog.debug("%s data remains", this);
                    break;
                }
            }

            synchronized (this) {
                writeQueue.remove(0);
            }

            // packet send complete
            wUnit.done();
        }

        NextSocketAction state;
        if(writeQueue.isEmpty()) {
            if(finale) {
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
    public boolean checkTimeout(Channel chkCh, int durationSec) {
        checkChannel(chkCh);

        return dataListener.checkTimeout(durationSec);
    }

    @Override
    public void onError(Channel chkCh, Throwable e) {
        checkChannel(chkCh);
        //BayLog.trace("%s onError: %s", this, e);

        try {
            throw e;
        }
        catch(SSLException ex) {
            if(traceSSL)
                BayLog.error(e, "%s SSL Error: %s", this, e);
            else
                BayLog.debug(e, "%s SSL Error: %s", this, e);
        }
        catch(Throwable ex) {
            BayLog.error(e);
        }
    }

    @Override
    public void onClosed(Channel chkCh) {
        try {
            checkChannel(chkCh);
        }
        catch(IllegalStateException e) {
            BayLog.error(e);
            return;
        }

        setValid(false);

        synchronized (this) {
            // Clear queue
            for (WriteUnit wu : writeQueue) {
                wu.done();
            }
            writeQueue.clear();
        }

        dataListener.notifyClose();
    }


    /////////////////////////////////////////////////////////////////////////////////
    // other methods
    /////////////////////////////////////////////////////////////////////////////////

    public final void flush() {
        checkInitialized();

        BayLog.debug("%s flush", this);

        if(chValid) {
            if (!writeQueue.isEmpty()) {
                BayLog.debug("%s flush->askToWrite", this);
                nonBlockingHandler.askToWrite(ch);
            }
        }
    }

    public final void postEnd() {
        checkInitialized();

        BayLog.debug("%s postEnd vld=%s", this, chValid);

        // setting order is QUITE important  finalState->finale
        this.finale = true;

        if(chValid) {
            if (!writeQueue.isEmpty()) {
                BayLog.debug("%s postEnd->askToWrite len=%d", this, writeQueue.size());
                nonBlockingHandler.askToWrite(ch);
            }
        }
    }

    private void checkChannel(Channel chkCh) {
        if(chkCh != this.ch)
            throw new Sink("Invalid transporter instance (ship was returned?)");
    }

    private void checkInitialized() {
        if(!initialized)
            throw new Sink("Transporter not initialized");
    }

    private void setValid(boolean valid) {
        chValid = valid;
    }


}
