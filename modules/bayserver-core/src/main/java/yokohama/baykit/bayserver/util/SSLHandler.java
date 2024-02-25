package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;

public class SSLHandler implements Reusable {

    public interface Reader {
        void read(ByteBuffer buf) throws IOException;
    }

    public interface Writer {
        void write(ByteBuffer buf) throws IOException;
    }

    public enum HandshakeState {
        NeedRead,
        Done,
        DoneRemains,
    }

    public final SSLContext ctx;
    SSLWrapper sslWrapper;
    ByteBuffer netIn, netOut, appIn;
    final boolean traceSSL;

    public SSLHandler(SSLContext ctx,  String[] appProtocols, boolean serverMode, boolean traceSSL) {
        this.ctx = ctx;
        this.sslWrapper = new SSLWrapper(ctx, appProtocols, serverMode);
        this.traceSSL = traceSSL;

        if (netIn == null)
            netIn = ByteBuffer.allocate(sslWrapper.engine.getSession().getPacketBufferSize());
        netIn.limit(0);
        if (netOut == null)
            netOut = ByteBuffer.allocate(sslWrapper.engine.getSession().getPacketBufferSize());
        netOut.limit(0);
        if (appIn == null)
            appIn = ByteBuffer.allocate(sslWrapper.engine.getSession().getApplicationBufferSize());
    }

    @Override
    public void reset() {
        netIn.limit(0);
        netOut.limit(0);
        appIn.clear();
        sslWrapper = null;
    }

    public HandshakeState handshake(Reader r, Writer w, boolean readable) throws IOException {
        if(readable)
            r.read(netIn);

        SSLEngineResult.HandshakeStatus status = sslWrapper.engine.getHandshakeStatus();
        if(status == NOT_HANDSHAKING) {
            sslWrapper.engine.beginHandshake();
            status = sslWrapper.engine.getHandshakeStatus();
        }
        while (true) {
            if(traceSSL) {
                BayLog.info(this + " SSL: handshake status: " + status);
            }

            if (status == null) {
                throw new EOFException(this + " SSL Connection is finished by peer.");
            }

            switch (status) {
                case NEED_UNWRAP: // need to read
                    if (!netIn.hasRemaining()) {
                        return HandshakeState.NeedRead;
                    }

                    status = sslWrapper.unwrap(netIn, appIn);
                    continue;

                case NEED_WRAP: {
                    ByteBuffer appOut = ByteBuffer.allocate(0);
                    netOut.clear();
                    status = sslWrapper.wrap(appOut, netOut);
                    netOut.flip();

                    // In handshaking, maybe we can write data without blocking because packet size is small
                    w.write(netOut);
                    continue;
                }

                case FINISHED:
                case NOT_HANDSHAKING:  // TLSv1.3
                    appIn.flip();
                    String pcl = SSLUtil.getApplicationProtocol(sslWrapper.engine);
                    if(netIn.hasRemaining())
                        netIn.compact();

                    if(netIn.hasRemaining() || appIn.hasRemaining())
                        return HandshakeState.DoneRemains;
                    else
                        return HandshakeState.Done;

                default:
                    throw new IOException("Invalid status in handshaking: " + status.toString());
            }
        }
    }


    public ByteBuffer onReadable(Reader r) throws IOException{

        r.read(netIn);

        if(appIn.hasRemaining()) {
            BayLog.debug("Remains appIn data (may be generated on handshaking)");
            return appIn;
        }

        appIn.clear();
        SSLEngineResult.HandshakeStatus status = sslWrapper.unwrap(netIn, appIn);
        if (status == null)
            throw new EOFException("SSL connection closed by peer");
        if (status != NOT_HANDSHAKING && status != FINISHED)
            throw new IllegalStateException("Illegal handshake status: " + status);
        appIn.flip();
        if(traceSSL)
            BayLog.info(this + " SSL: Decrypt " + netIn.position() + "->" + appIn.limit() + " bytes");

        return appIn;
    }

    public boolean onWritable(Writer w, ByteBuffer appOut) throws IOException {
        do {
            if (!netOut.hasRemaining()) {
                netOut.clear();
                SSLEngineResult.HandshakeStatus hstatus = sslWrapper.wrap(appOut, netOut);
                if (hstatus != NOT_HANDSHAKING)
                    throw new IllegalStateException();

                if (traceSSL)
                    BayLog.info(this + " SSL: Encrypt " + appOut.position() + " bytes -> " + netOut.position() + " bytes");

                netOut.flip();
            }

            w.write(netOut);

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

    public String getApplicationProtocol() {
        return SSLUtil.getApplicationProtocol(sslWrapper.engine);
    }
}
