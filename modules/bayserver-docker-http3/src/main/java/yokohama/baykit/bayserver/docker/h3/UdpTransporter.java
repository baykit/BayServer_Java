package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.agent.multiplexer.TransporterBase;
import yokohama.baykit.bayserver.common.Rudder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class UdpTransporter extends TransporterBase {

    final ByteBuffer readBuf;

    public UdpTransporter(boolean serverMode, int bufsiz) {
        super(serverMode, false);
        readBuf = ByteBuffer.allocate(bufsiz);
    }

    public void init() {
        needHandshake = false;
    }


    @Override
    public String toString() {
        return "utp[]";
    }

    ////////////////////////////////////////////
    // implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {
        if(readBuf != null)
            readBuf.clear();
    }

    ////////////////////////////////////////////
    // implements Transporter
    ////////////////////////////////////////////
    @Override
    protected boolean handshake(Rudder rd, DataListener lis, boolean readable) throws IOException {
        throw new IllegalStateException("This transporter does not need handshake");
    }

    @Override
    protected ByteBuffer readNonBlock(Rudder rd, InetSocketAddress[] adr) throws IOException {

        // read data
        readBuf.clear();
        InetSocketAddress sender = (InetSocketAddress) ((DatagramChannel) ChannelRudder.getChannel(rd)).receive(readBuf);
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
    protected boolean writeNonBlock(Rudder rd, InetSocketAddress adr, ByteBuffer buf) throws IOException {
        int pos = buf.position();

        int len = ((DatagramChannel)ChannelRudder.getChannel(rd)).send(buf, adr);
        BayLog.trace("%s wrote %d bytes remain=%d", this,  (buf.position() - pos), buf.remaining());

        return !buf.hasRemaining();
    }

    @Override
    protected boolean secure() {
        return false;
    }
}
