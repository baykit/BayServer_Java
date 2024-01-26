package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Rudder;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class PlainTransporter extends TransporterBase {

    final ByteBuffer readBuf;

    public PlainTransporter(boolean serverMode, int bufsiz) {
        super(serverMode, false);
        readBuf = ByteBuffer.allocate(bufsiz);
        needHandshake = false;
    }


    @Override
    public void reset() {
        if(readBuf != null)
            readBuf.clear();
    }


    @Override
    public String toString() {
        return "tp[]";
    }

    /////////////////////////////////////////////////////////////////////////////////
    // implements Transporter
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    protected boolean handshake(Rudder rd, DataListener lis, boolean readable) throws IOException {
        throw new IllegalStateException("This transporter does not need handshake");
    }

    @Override
    protected ByteBuffer readNonBlock(Rudder rd, InetSocketAddress[] adr) throws IOException {

        // read data
        readBuf.clear();
        int c = ((ReadableByteChannel)ChannelRudder.getChannel(rd)).read(readBuf);
        if (c == -1)
            throw new EOFException();
        readBuf.flip();
        BayLog.trace("%s read %d bytes", this, readBuf.limit());

        return readBuf;
    }

    @Override
    protected boolean writeNonBlock(Rudder rd, InetSocketAddress adr, ByteBuffer buf) throws IOException {
        int pos = buf.position();
        ((WritableByteChannel)ChannelRudder.getChannel(rd)).write(buf);
        BayLog.trace(this + " wrote " + (buf.position() - pos) + " bytes");

        return !buf.hasRemaining();
    }

    @Override
    protected boolean secure() {
        return false;
    }
}
