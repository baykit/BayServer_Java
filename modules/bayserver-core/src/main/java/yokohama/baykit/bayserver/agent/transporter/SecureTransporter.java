package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NonBlockingHandler;
import yokohama.baykit.bayserver.common.Valve;
import yokohama.baykit.bayserver.util.SSLHandler;
import yokohama.baykit.bayserver.util.SSLUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;

/**
 * Transporter for secure TCP/IP connection
 */
public class SecureTransporter extends Transporter {

    final SSLContext ctx;
    final String[] appProtocols;

    SSLHandler sslh;
    ByteBuffer netIn, netOut, appIn;

    public SecureTransporter(SSLContext ctx, String[] appProtocols, boolean serverMode, boolean traceSSL) {
        super(serverMode, traceSSL);
        this.ctx = ctx;
        this.appProtocols = appProtocols;
    }

    @Override
    public void init(Channel ch, DataListener lis, Valve vlv) {
        super.init(ch, lis, vlv);
        sslh = new SSLHandler(ctx, appProtocols, serverMode);
        if (netIn == null)
            netIn = ByteBuffer.allocate(sslh.engine.getSession().getPacketBufferSize());
        netIn.limit(0);
        if (netOut == null)
            netOut = ByteBuffer.allocate(sslh.engine.getSession().getPacketBufferSize());
        netOut.limit(0);
        if (appIn == null)
            appIn = ByteBuffer.allocate(sslh.engine.getSession().getApplicationBufferSize());
    }

    @Override
    public void reset() {
        super.reset();
        netIn.limit(0);
        netOut.limit(0);
        appIn.clear();
        needHandshake = true;
        sslh = null;
    }


    @Override
    public String toString() {
        return "stp[" + dataListener + "]";
    }


    /////////////////////////////////////////////////////////////////////////////////
    // implements Transporter
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    protected boolean handshake(boolean readable) throws IOException {
        if(readable)
            readNetIn();

        SSLEngineResult.HandshakeStatus status = sslh.engine.getHandshakeStatus();
        if(status == NOT_HANDSHAKING) {
            sslh.engine.beginHandshake();
            status = sslh.engine.getHandshakeStatus();
        }
        while (true) {
            if(traceSSL)
                BayLog.info(this + " SSL: handshake status: " + status);
            //if(sslh.engine.getHandshakeSession() != null)
            //   BayLog.debug(this + " protocol=" + sslh.engine.getHandshakeSession().getProtocol());
            if (status == null) {
                throw new EOFException(this + " SSL Connection is finished by peer.");
            }

            switch (status) {
                case NEED_UNWRAP: // need to read
                    if (!netIn.hasRemaining())
                        throw new WaitReadableException();
 //                   BayLog.debug(this + " Before unwrap: netIn=" + netIn.position() + "/" + netIn.limit() + " appIn=" + appIn.position() + "/" + appIn.limit());
                    status = sslh.unwrap(netIn, appIn);
  //                  BayLog.debug(this + " After unwrap: netIn=" + netIn.position() + "/" + netIn.limit() + " appIn=" + appIn.position() + "/" + appIn.limit());
                    continue;

                case NEED_WRAP: {
                    ByteBuffer appOut = ByteBuffer.allocate(0);
                    netOut.clear();
   //                 BayLog.debug(this + " Before wrap: appOut" + appOut.position() + "/" + appOut.limit() + " netOut=" + netOut.position() + "/" + netOut.limit());
                    status = sslh.wrap(appOut, netOut);
                    netOut.flip();
    //                BayLog.debug(this + " After wrap: appOut" + appOut.position() + "/" + appOut.limit() + " netOut=" + netOut.position() + "/" + netOut.limit());

 // In handshaking, maybe we can write data without blocking because packet size is small
                    writeNetOut();
                    continue;
                }

                case FINISHED:
                case NOT_HANDSHAKING:  // TLSv1.3
                    appIn.flip();
//                    BayLog.debug(this + " Handshake finished. status=" + status + " remain: net=" + netIn.remaining() + " app=" + appIn.remaining());
                    needHandshake = false;
                    String pcl = SSLUtil.getApplicationProtocol(sslh.engine);
                    dataListener.notifyHandshakeDone(pcl);
                    if(netIn.hasRemaining())
                        netIn.compact();
                    return netIn.hasRemaining() || appIn.hasRemaining();

                default:
                    throw new IOException("Invalid status in handshaking: " + status.toString());
            }
        }
    }

    @Override
    protected ByteBuffer readNonBlock(InetSocketAddress[] adr) throws IOException {
        if(appIn.hasRemaining()) {
            BayLog.debug("Remains appIn data (may be generated on handshaking)");
            return appIn;
        }

        readNetIn();

        appIn.clear();
        SSLEngineResult.HandshakeStatus status = sslh.unwrap(netIn, appIn);
        if (status == null)
            throw new EOFException("SSL connection closed by peer");
        if (status != NOT_HANDSHAKING && status != FINISHED)
            throw new IllegalStateException("Illegal handshake status: " + status);
        appIn.flip();
        if(traceSSL)
            BayLog.info(this + " SSL: Decrypt " + netIn.position() + "->" + appIn.limit() + " bytes");

        return appIn;
    }

    @Override
    protected boolean writeNonBlock(ByteBuffer appOut, InetSocketAddress adr) throws IOException {
        do {
            if (!netOut.hasRemaining()) {
                netOut.clear();
                SSLEngineResult.HandshakeStatus hstatus = sslh.wrap(appOut, netOut);
                if (hstatus != NOT_HANDSHAKING)
                    throw new IllegalStateException();

                if (traceSSL)
                    BayLog.info(this + " SSL: Encrypt " + appOut.position() + " bytes -> " + netOut.position() + " bytes");

                netOut.flip();
            }

            writeNetOut();

        } while (appOut.hasRemaining() && !netOut.hasRemaining());

        if (appOut.hasRemaining()) {
            // if application data remains, challenge on next writable chance.
            return false;
        }

        if (netOut.hasRemaining()) {
            // if Network data remains, challenge on next writable chance.
            return false;
        }

        return true;
    }

    @Override
    protected boolean secure() {
        return true;
    }


    private void readNetIn() throws IOException {
        if(!netIn.hasRemaining())
            netIn.clear();

        int oldPos = netIn.position();
        int c = ((SocketChannel)ch).read(netIn);
        if (c == -1) {
            throw new EOFException("Closed by peer.");
        }
        netIn.flip();
        if (BayLog.isTraceMode())
            BayLog.trace(this + " Read " + (netIn.limit() - oldPos) + " encrypted bytes");
    }

    private void writeNetOut() throws IOException {
        int npos = netOut.position();
        ((SocketChannel)ch).write(netOut);
        if (BayLog.isTraceMode())
            BayLog.trace(this + " Wrote " + (netOut.position() - npos) + " encrypted bytes(" + netOut.position() + "/" + netOut.limit() + ")");
    }
}

