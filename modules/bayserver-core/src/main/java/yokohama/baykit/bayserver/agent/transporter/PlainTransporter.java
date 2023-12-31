package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NonBlockingHandler;
import yokohama.baykit.bayserver.common.Valve;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class PlainTransporter extends Transporter {

    final ByteBuffer readBuf;

    public PlainTransporter(boolean serverMode, int bufsiz, boolean writeOnly) {
        super(serverMode, false, writeOnly);
        readBuf = ByteBuffer.allocate(bufsiz);
    }

    public PlainTransporter(boolean serverMode, int bufsiz) {
        this(serverMode, bufsiz, false);
    }

    @Override
    public void init(Channel ch, DataListener lis, Valve vlv) {
        super.init(ch, lis, vlv);
        needHandshake = false;
    }

    @Override
    public void reset() {
        super.reset();
        if(readBuf != null)
            readBuf.clear();
    }


    @Override
    public String toString() {
        return "tp[" + dataListener + "]";
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements Transporter
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    protected boolean handshake(boolean readable) throws IOException {
        throw new IllegalStateException("This transporter does not need handshake");
    }

    @Override
    protected ByteBuffer readNonBlock(InetSocketAddress[] adr) throws IOException {

        // read data
        readBuf.clear();
        int c = ((ReadableByteChannel)ch).read(readBuf);
        if (c == -1)
            throw new EOFException();
        readBuf.flip();
        BayLog.trace(this + " read " + readBuf.limit() + " bytes");

        return readBuf;
    }

    @Override
    protected boolean writeNonBlock(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        int pos = buf.position();
        ((WritableByteChannel)ch).write(buf);
        BayLog.trace(this + " wrote " + (buf.position() - pos) + " bytes");

        return !buf.hasRemaining();
    }

    @Override
    protected boolean secure() {
        return false;
    }
}
