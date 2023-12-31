package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NonBlockingHandler;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.common.Valve;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;

public class UdpTransporter extends Transporter {

    final ByteBuffer readBuf;

    public UdpTransporter(boolean serverMode, int bufsiz) {
        super(serverMode, false);
        readBuf = ByteBuffer.allocate(bufsiz);
    }

    @Override
    public void init(Channel ch, DataListener lis, Valve vlv) {
        super.init(ch, lis, vlv);
        needHandshake = false;
    }


    @Override
    public String toString() {
        return "tp[" + dataListener + "]";
    }

    ////////////////////////////////////////////
    // implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        if(readBuf != null)
            readBuf.clear();
    }

    ////////////////////////////////////////////
    // implements Transporter
    ////////////////////////////////////////////
    @Override
    protected boolean handshake(boolean readable) throws IOException {
        throw new IllegalStateException("This transporter does not need handshake");
    }

    @Override
    protected ByteBuffer readNonBlock(InetSocketAddress[] adr) throws IOException {

        // read data
        readBuf.clear();
        InetSocketAddress sender = (InetSocketAddress) ((DatagramChannel)ch).receive(readBuf);
        if (sender == null) {
            BayLog.trace("%s Empty packet data (Maybe another agent received data)", this);
            return null;
        }
        readBuf.flip();
        BayLog.trace(this + " read " + readBuf.limit() + " bytes");

        adr[0] = sender;
        return readBuf;
    }

    @Override
    protected boolean writeNonBlock(ByteBuffer buf, InetSocketAddress adr) throws IOException {
        int pos = buf.position();

        int len = ((DatagramChannel)ch).send(buf, adr);
        BayLog.trace("%s wrote %d bytes remain=%d", this,  (buf.position() - pos), buf.remaining());

        return !buf.hasRemaining();
    }

    @Override
    protected boolean secure() {
        return false;
    }
}
