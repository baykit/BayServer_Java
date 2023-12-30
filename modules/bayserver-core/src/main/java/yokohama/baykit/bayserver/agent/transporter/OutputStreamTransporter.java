package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Postman;
import yokohama.baykit.bayserver.common.Valve;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Transporter for InputStream/OutputStream
 */
public class OutputStreamTransporter implements ChannelListener<OutputStream>, Reusable, Postman {

    int agentId;
    DataListener dataListener;
    OutputStream stream;
    Valve valve;
    protected ArrayList<ByteBuffer> writeQueue = new ArrayList<>();
    boolean initialized = false;

    public OutputStreamTransporter(int agtId) {
        this.agentId = agtId;
    }

    public void init(OutputStream stm, DataListener lis, Valve vlv) {

        if(initialized)
            throw new Sink(this + " This transporter is already in use by channel: " + stream);
        if(!writeQueue.isEmpty())
            throw new Sink();

        this.dataListener = lis;
        this.stream = stm;
        this.valve = vlv;
        this.initialized = true;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        dataListener = null;
        stream = null;
        writeQueue.clear();
        initialized = false;
    }


    /////////////////////////////////////
    // Implements ChannelListener
    /////////////////////////////////////

    @Override
    public NextSocketAction onReadable(OutputStream chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction onWritable(OutputStream chkCh) throws IOException {
        checkStream(chkCh);

        while(true) {
            ByteBuffer buf;
            synchronized (writeQueue) {
                if(writeQueue.isEmpty())
                    break;
                buf = writeQueue.remove(0);
            }
            stream.write(buf.array(), 0, buf.limit());
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction onConnectable(OutputStream chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public void onError(OutputStream chkCh, Throwable e) {
        checkStream(chkCh);

        BayLog.debug(e);
    }

    @Override
    public void onClosed(OutputStream chkCh) {
        checkStream(chkCh);

        dataListener.notifyClose();
    }

    @Override
    public boolean checkTimeout(OutputStream chkCh, int durationSec) {
        return dataListener.checkTimeout(durationSec);
    }

    /////////////////////////////////////////
    // Implements Postman
    /////////////////////////////////////////

    @Override
    public void post(ByteBuffer buf, InetSocketAddress adr, Object tag, DataConsumeListener listener) throws IOException {
        synchronized (writeQueue) {
            boolean empty = writeQueue.isEmpty();
            writeQueue.add(buf);
            if(empty)
                valve.openValve();
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void postEnd() {

    }

    @Override
    public boolean isZombie() {
        return false;
    }

    @Override
    public void abort() {

    }

    /////////////////////////////////////
    // Private methods
    /////////////////////////////////////

    private void checkStream(Closeable chkCh) {
        if(chkCh != stream)
            throw new Sink("Invalid transporter instance (ship was returned?)");
    }
}
