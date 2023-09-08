package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;

import javax.net.ssl.*;
import java.nio.ByteBuffer;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

public class SSLHandler {

    public final SSLEngine engine;
    int appBufSize;
    int netBufSize;

    public SSLHandler(SSLContext ctx, String[] appProtocols, boolean serverMode) {
        this.engine = ctx.createSSLEngine();
        engine.setUseClientMode(!serverMode);
        appBufSize = engine.getSession().getApplicationBufferSize();
        netBufSize = engine.getSession().getPacketBufferSize();
        if(appProtocols != null) {
            SSLParameters params = new SSLParameters();
            SSLUtil.setApplicationProtocols(params, new String[]{"h2"});
            engine.setSSLParameters(params);
        }
    }

    public ByteBuffer allocAppBuf() {
        return ByteBuffer.allocate(appBufSize);
    }

    public ByteBuffer allocNetBuf() {
        return ByteBuffer.allocate(netBufSize);
    }

    /**
     * unwrap data
     * @param netIn
     * @param appIn
     * @return NOT_HANDSHAKING, NEED_UNWRAP or NEED_WRAP
     * @throws SSLException
     */
    public SSLEngineResult.HandshakeStatus unwrap(ByteBuffer netIn, ByteBuffer appIn) throws SSLException {
        if(BayLog.isTraceMode())
            BayLog.trace("SSL: unwrap");

        while(true) {
            int oldPos = appIn.position();
            int oldLimit = appIn.limit();
            // Unwrap net buffer data and put it into application buffer
            SSLEngineResult res = engine.unwrap(netIn, appIn);
            switch (res.getStatus()) {
                case BUFFER_OVERFLOW: {
                    ByteBuffer newAppIn = ByteBuffer.allocate(appIn.capacity() * 2);
                    newAppIn.put(appIn.array(), 0, appIn.position());
                    appIn = newAppIn;
                    continue;
                }

                case BUFFER_UNDERFLOW:
                    netIn.compact();
                    return res.getHandshakeStatus();

                case CLOSED:
                    return null;

                case OK: {
                    if (res.getHandshakeStatus() == NOT_HANDSHAKING) {
                        if(netIn.hasRemaining())
                            continue;
                        else
                            return NOT_HANDSHAKING;
                    }
                    else {
                        SSLEngineResult.HandshakeStatus hstat = handshake(res);
                        if (hstat == FINISHED && netIn.hasRemaining())
                            continue;
                        else
                            return hstat;
                    }
                }

                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * wrap data
     * @param appOut
     * @param netOut
     * @return NOT_HANDSHAKING, NEED_UNWRAP, NEED_WRAP or FINISHED
     * @throws SSLException
     */
    public SSLEngineResult.HandshakeStatus wrap(ByteBuffer appOut, ByteBuffer netOut) throws SSLException {
        //if(BayLog.isTraceMode())
        //    BayLog.trace ("SSL: wrap");

        // Wrap application output data and put it into network output buffer
        SSLEngineResult res = engine.wrap(appOut, netOut);
        if (res.getStatus() != SSLEngineResult.Status.OK)
            throw new SSLException("wrap: Illegal SSL Engine status: " + res.getStatus());

        if(res.getHandshakeStatus() == NOT_HANDSHAKING)
            return NOT_HANDSHAKING;

        SSLEngineResult.HandshakeStatus hstat = handshake(res);
        return hstat;
    }


    private SSLEngineResult.HandshakeStatus handshake(SSLEngineResult res) throws SSLException {
        SSLEngineResult.HandshakeStatus oldStat = res.getHandshakeStatus();
        SSLEngineResult.HandshakeStatus hstat = oldStat;
        switch(hstat) {
            case NOT_HANDSHAKING:
                break;

            case NEED_TASK:
                hstat = doTasks();
                break;

            case FINISHED:
                break;

            case NEED_WRAP: {
                break;
            }
        }
        if(BayLog.isTraceMode())
            BayLog.trace("SSL: handshake: " + oldStat + "->" + hstat);
        return hstat;
    }

    private SSLEngineResult.HandshakeStatus doTasks() {
        while (true) {
            Runnable rbl = engine.getDelegatedTask();
            if(rbl == null)
                break;
            if(BayLog.isTraceMode())
                BayLog.trace("Handshake: Run delegated task");
            rbl.run();
        }
        return engine.getHandshakeStatus();
    }
}
