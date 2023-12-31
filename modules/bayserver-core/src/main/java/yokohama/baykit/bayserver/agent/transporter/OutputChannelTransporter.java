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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;

/**
 * Transporter for InputStream/OutputStream
 */
public class OutputChannelTransporter implements ChannelListener, Reusable, Postman {

    int agentId;
    DataListener dataListener;
    Channel channel;
    Valve valve;
    protected final ArrayList<ByteBuffer> writeQueue = new ArrayList<>();
    boolean initialized = false;

    public OutputChannelTransporter(int agtId) {
        this.agentId = agtId;
    }

    public void init(Channel ch, DataListener lis, Valve vlv) {

        if(initialized)
            throw new Sink(this + " This transporter is already in use by channel: " + channel);
        if(!writeQueue.isEmpty())
            throw new Sink();

        this.dataListener = lis;
        this.channel = ch;
        this.valve = vlv;
        this.initialized = true;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        dataListener = null;
        channel = null;
        writeQueue.clear();
        initialized = false;
    }


    /////////////////////////////////////
    // Implements ChannelListener
    /////////////////////////////////////

    @Override
    public NextSocketAction onReadable(Channel chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction onWritable(Channel chkCh) throws IOException {
        checkStream(chkCh);

        while(true) {
            ByteBuffer buf;
            synchronized (writeQueue) {
                if(writeQueue.isEmpty())
                    break;
                buf = writeQueue.remove(0);
            }
            ((WritableByteChannel)channel).write(buf);
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction onConnectable(Channel chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public void onError(Channel chkCh, Throwable e) {
        checkStream(chkCh);

        BayLog.debug(e);
    }

    @Override
    public void onClosed(Channel chkCh) {
        checkStream(chkCh);

        dataListener.notifyClose();
    }

    @Override
    public boolean checkTimeout(Channel chkCh, int durationSec) {
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
                valve.openWriteValve();
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
        if(chkCh != channel)
            throw new Sink("Invalid transporter instance (ship was returned?)");
    }
}
