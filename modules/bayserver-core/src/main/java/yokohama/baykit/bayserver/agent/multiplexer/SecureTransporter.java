package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

/**
 * Transporter for secure TCP/IP connection
 */
public class SecureTransporter extends PlainTransporter {

    public enum HandshakeState {
        NeedRead,
        Done,
        DoneRemains,
    }

    private final SSLContext sslContext;
    private final String[] appProtocols;
    private SSLWrapper sslWrapper;
    private ObjectStore<ReusableByteBuffer> appInStore;
    private ObjectStore<ReusableByteBuffer> netOutStore;

    public SecureTransporter(
            Multiplexer mpx,
            Ship sip,
            boolean serverMode,
            int readBufferSize,
            boolean traceSSL,
            SSLContext ctx,
            String[] appProtocols) {
        super(mpx, sip, serverMode, readBufferSize, traceSSL);
        this.sslContext = ctx;
        this.appProtocols = appProtocols;
    }

    @Override
    public void init() {
        super.init();
        this.sslWrapper = new SSLWrapper(sslContext, appProtocols, serverMode);
        if (this.netOutStore == null) {
            this.readBufferSize = sslWrapper.engine.getSession().getPacketBufferSize();
            this.netOutStore = new ObjectStore<>(new ReusableByteBufferFactory(sslWrapper.engine.getSession().getPacketBufferSize()));
            this.appInStore = new ObjectStore<>(new ReusableByteBufferFactory(sslWrapper.engine.getSession().getApplicationBufferSize()));
        }
    }

    @Override
    public void reset() {
        this.sslWrapper = null;
        this.netOutStore.reset();
        this.appInStore.reset();
    }

    @Override
    public String toString() {
        return "stp[" + ship + "]";
    }

    ////////////////////////////////////////////
    // implements Transporter
    ////////////////////////////////////////////
    @Override
    public NextSocketAction onRead(Rudder rd, ByteBuffer netIn, InetSocketAddress adr) throws IOException {
        BayLog.debug("%s onRead: buf=%s", this, netIn);

        if(netIn.limit() == 0)
            return ship.notifyEof();

        ReusableByteBuffer appIn = appInStore.rent();
        try {
            if(needHandshake) {
                boolean remain;
                try {
                    remain = handshake(rd, netIn, appIn.buffer);
                }
                catch(WaitReadableException e) {
                    return NextSocketAction.Continue;
                }


                // Handshake is done
                if(!remain)  {
                    // no ramaining process (maybe next process is writing)
                    return NextSocketAction.Continue;
                }
                else if (appIn.buffer.hasRemaining()) {
                    BayLog.debug("Remains appIn data (may be generated on handshaking)");
                    return super.onRead(rd, appIn.buffer, adr);
                }
            }

            NextSocketAction nextAct = NextSocketAction.Continue;
            loop:
            while(true) {
                appIn.buffer.clear();
                SSLEngineResult res = sslWrapper.engine.unwrap(netIn, appIn.buffer);
                //BayLog.debug("%s unwrapped: netIn=%s appIn=%s", this, netIn, appIn.buffer);

                switch (res.getStatus()) {
                    case CLOSED:
                        // Handles as EOF
                        BayLog.debug("%s SSL connection closed by peer", this);
                        appIn.buffer.limit(0);
                        break;

                    case BUFFER_OVERFLOW:
                        BayLog.debug("Buffer overflow! appIn=%s", appIn.buffer);
                    case OK:
                        appIn.buffer.flip();
                        break;

                    case BUFFER_UNDERFLOW:
                        BayLog.debug("Buffer underflow! appIn=%s", appIn.buffer);
                        break loop;

                    default:
                        throw new IllegalStateException("Illegal unwrapper status: " + res.getStatus());
                }

                if (traceSSL)
                    BayLog.info(this + " SSL: Decrypt " + netIn.position() + "->" + appIn.buffer.limit() + " bytes");

                nextAct = super.onRead(rd, appIn.buffer, adr);
                if(nextAct != NextSocketAction.Continue)
                    break;
            }

            return nextAct;
        }
        finally {
            appInStore.Return(appIn);
        }
    }

    @Override
    public void reqWrite(
            Rudder rd,
            ByteBuffer appOut,
            InetSocketAddress adr,
            Object tag,
            DataConsumeListener listener)
            throws IOException {

        BayLog.trace("%s reqWrite(secure): %s", appOut);
        if(closed) {
            throw new IOException("Channel is closed");
        }

        if(needHandshake) {
            throw new IOException("Cannot request to write during handshaking");
            /*try {
                handshake(rd, ByteBuffer.allocate(0));
            }
            catch(WaitReadableException e) {
                multiplexer.reqRead(rd);
            }
             */
        }

        // Encrypt data and request to write it
        do {
            ReusableByteBuffer netOut = netOutStore.rent();
            SSLEngineResult.HandshakeStatus hstatus = sslWrapper.wrap(appOut, netOut.buffer);
            if (hstatus != NOT_HANDSHAKING)
                throw new IllegalStateException();

            if (traceSSL)
                BayLog.info(this + " SSL: Encrypt " + appOut.position() + " bytes -> " + netOut.buffer.position() + " bytes");

            netOut.buffer.flip();
            if(appOut.hasRemaining()) {
                super.reqWrite(rd, netOut.buffer, adr, tag, () -> {
                    netOutStore.Return(netOut);
                });
            }
            else {
                super.reqWrite(rd, netOut.buffer, adr, tag, () -> {
                    netOutStore.Return(netOut);
                    listener.dataConsumed();
                });
            }

        } while (appOut.hasRemaining());
    }

    @Override
    protected boolean secure() {
        return true;
    }

    /**
     * print memory usage
     */
    @Override
    public synchronized void printUsage(int indent) {
        BayLog.info("%s%s usage:", StringUtil.indent(indent), this);
        BayLog.info("%sNet Out Buffer:", StringUtil.indent(indent));
        netOutStore.printUsage(indent + 1);
        BayLog.info("%sApp In Buffer:", StringUtil.indent(indent));
        appInStore.printUsage(indent + 1);
    }

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    protected boolean handshake(Rudder rd, ByteBuffer netIn, ByteBuffer appIn) throws IOException {

        HandshakeState state = doHandshake(rd, netIn, appIn);

        switch(state) {
            case NeedRead:
                throw new WaitReadableException();

            case Done:
            case DoneRemains:
                needHandshake = false;
                ship.notifyHandshakeDone(getApplicationProtocol());
                return state == HandshakeState.DoneRemains;

            default:
                throw new IllegalStateException();
        }
    }


    private HandshakeState doHandshake(Rudder rd, ByteBuffer netIn, ByteBuffer appIn) throws IOException {

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
                    ReusableByteBuffer netOut = netOutStore.rent();
                    ByteBuffer appOut = ByteBuffer.allocate(0);
                    status = sslWrapper.wrap(appOut, netOut.buffer);
                    netOut.buffer.flip();

                    // In handshaking, maybe we can write data without blocking because packet size is small
                    rd.write(netOut.buffer);
                    netOutStore.Return(netOut);
                    continue;
                }

                case FINISHED:
                case NOT_HANDSHAKING:  // TLSv1.3
                    appIn.flip();
                    String pcl = SSLUtil.getApplicationProtocol(sslWrapper.engine);
                    //if(netIn.hasRemaining())
                    //    netIn.compact();

                    if(netIn.hasRemaining() || appIn.hasRemaining())
                        return HandshakeState.DoneRemains;
                    else
                        return HandshakeState.Done;

                default:
                    throw new IOException("Invalid status in handshaking: " + status.toString());
            }
        }

    }

    private String getApplicationProtocol() {
        return SSLUtil.getApplicationProtocol(sslWrapper.engine);
    }
}

