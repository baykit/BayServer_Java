package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.SSLHandler;

import javax.net.ssl.SSLContext;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Transporter for secure TCP/IP connection
 */
public class SecureTransporter extends TransporterBase {

    SSLHandler sslHandler;

    public SecureTransporter(SSLContext ctx, String[] appProtocols, boolean serverMode, boolean traceSSL) {
        super(serverMode, traceSSL);
        sslHandler = new SSLHandler(ctx, appProtocols, serverMode, traceSSL);
    }

    @Override
    public void reset() {
        sslHandler.reset();
    }

    @Override
    public String toString() {
        return "stp[]";
    }


    /////////////////////////////////////////////////////////////////////////////////
    // implements Transporter
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    protected boolean handshake(Rudder rd, DataListener lis, boolean readable) throws IOException {

        SSLHandler.HandshakeState state =
                sslHandler.handshake(
                        buf -> readNetIn(rd, buf),
                        buf -> writeNetOut(rd, buf),
                        readable);

        switch(state) {
            case NeedRead:
                throw new WaitReadableException();

            case Done:
            case DoneRemains:
                needHandshake = false;
                lis.notifyHandshakeDone(sslHandler.getApplicationProtocol());
                return state == SSLHandler.HandshakeState.DoneRemains;

            default:
                throw new IllegalStateException();
        }
    }

    @Override
    protected ByteBuffer readNonBlock(Rudder rd, InetSocketAddress[] adr) throws IOException {
        return sslHandler.onReadable(buf -> readNetIn(rd, buf));
    }

    @Override
    protected boolean writeNonBlock(Rudder rd, InetSocketAddress adr, ByteBuffer appOut) throws IOException {
        return sslHandler.onWritable(buf -> writeNetOut(rd, buf), appOut);
    }

    @Override
    protected boolean secure() {
        return true;
    }


    public void readNetIn(Rudder rd, ByteBuffer netIn) throws IOException {
        if(!netIn.hasRemaining())
            netIn.clear();

        int oldPos = netIn.position();
        int c = ((SocketChannel) ChannelRudder.getChannel(rd)).read(netIn);
        if (c == -1) {
            throw new EOFException("Closed by peer.");
        }
        netIn.flip();
        if (BayLog.isTraceMode())
            BayLog.trace(this + " Read " + (netIn.limit() - oldPos) + " encrypted bytes");
    }

    private void writeNetOut(Rudder rd, ByteBuffer netOut) throws IOException {
        int npos = netOut.position();
        ((SocketChannel) ChannelRudder.getChannel(rd)).write(netOut);
        if (BayLog.isTraceMode())
            BayLog.trace(this + " Wrote " + (netOut.position() - npos) + " encrypted bytes(" + netOut.position() + "/" + netOut.limit() + ")");
    }
}

