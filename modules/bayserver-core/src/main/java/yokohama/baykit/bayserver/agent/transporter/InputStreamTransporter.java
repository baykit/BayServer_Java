package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Transporter for InputStream/OutputStream
 */
public class InputStreamTransporter implements ChannelListener<InputStream>, Reusable {

    int agentId;
    DataListener dataListener;
    InputStream input;
    ByteBuffer buf;
    boolean initialized = false;

    public InputStreamTransporter(int agtId, int bufsize) {
        this.agentId = agtId;
        this.buf = ByteBuffer.allocate(bufsize);
    }

    public void init(InputStream stm, DataListener lis) {

        if(initialized)
            throw new Sink(this + " This transporter is already in use by channel: " + input);

        this.dataListener = lis;
        this.input = stm;
        this.initialized = true;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        dataListener = null;
        input = null;
        buf.reset();
        initialized = false;
    }


    /////////////////////////////////////
    // Implements ChannelListener
    /////////////////////////////////////

    @Override
    public NextSocketAction onReadable(InputStream chkCh) throws IOException {
        checkStream(chkCh);

        buf.clear();
        int readLen = input.read(buf.array(), 0, buf.capacity());
        if(readLen == -1) {
            dataListener.notifyEof();
            BayLog.debug("%s Detected EOF", this);
            return NextSocketAction.Close;
        }

        buf.position(readLen);
        buf.flip();

        return dataListener.notifyRead(buf, null);
    }

    @Override
    public NextSocketAction onWritable(InputStream chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public NextSocketAction onConnectable(InputStream chkCh) throws IOException {
        throw new Sink();
    }

    @Override
    public void onError(InputStream chkCh, Throwable e) {
        checkStream(chkCh);

        BayLog.debug(e);
    }

    @Override
    public void onClosed(InputStream chkCh) {
        checkStream(chkCh);

        dataListener.notifyClose();
    }

    @Override
    public boolean checkTimeout(InputStream chkCh, int durationSec) {
        return dataListener.checkTimeout(durationSec);
    }

    /////////////////////////////////////
    // Private methods
    /////////////////////////////////////

    private void checkStream(Closeable chkCh) {
        if(chkCh != input)
            throw new Sink("Invalid transporter instance (ship was returned?)");
    }
}
