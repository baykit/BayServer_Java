package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.UpgradeException;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class PlainTransporter implements Transporter, Reusable {

    protected static class WaitReadableException extends IOException {

    }

    protected final Multiplexer multiplexer;
    protected final boolean serverMode;
    protected final boolean traceSSL;
    protected int readBufferSize;
    protected boolean needHandshake = true;
    protected Ship ship;
    protected boolean closed = false;

    public PlainTransporter(
            Multiplexer multiplexer,
            Ship sip,
            boolean serverMode,
            int readBufferSize,
            boolean traceSSL) {
        this.multiplexer = multiplexer;
        this.ship = sip;
        this.serverMode = serverMode;
        this.traceSSL = traceSSL;
        this.readBufferSize = readBufferSize;
    }

    public String toString() {
        return "tp[" + ship + "]";
    }

    ////////////////////////////////////////////
    // implements Reusable
    ////////////////////////////////////////////

    @Override
    public void reset() {
        closed = false;
    }

    ////////////////////////////////////////////
    // implements Transporter
    ////////////////////////////////////////////

    @Override
    public void init() {
    }

    @Override
    public NextSocketAction onConnect(Rudder rd) throws IOException {
        BayLog.trace("%s onConnect", this);

        return ship.notifyConnect();
    }

    @Override
    public NextSocketAction onRead(Rudder rd, ByteBuffer buf, InetSocketAddress adr) throws IOException {
        BayLog.debug("%s onRead: %s", this, buf);


        if(buf.limit() == 0) {
            return ship.notifyEof();
        }
        else {
            try {
                return ship.notifyRead(buf);
            }
            catch(UpgradeException e) {
                BayLog.debug("%s Protocol upgrade", ship);
                buf.rewind();
                return ship.notifyRead(buf);
            }
            catch (ProtocolException e) {
                boolean close = ship.notifyProtocolError(e);
                if(!close && serverMode)
                    return NextSocketAction.Continue;
                else
                    return NextSocketAction.Close;
            }
            catch (IOException e) {
                // IOException which occur in notifyRead must be distinguished from
                // IOException which occur in handshake or readNonBlock.
                onError(rd, e);
                return NextSocketAction.Close;
            }
        }
    }

    @Override
    public void onError(Rudder rd, Throwable e) {
        ship.notifyError(e);
    }

    @Override
    public void onClosed(Rudder rd) {
        ship.notifyClose();
    }

    @Override
    public void reqConnect(Rudder rd, SocketAddress addr) throws IOException {
        multiplexer.reqConnect(rd, addr);
    }

    @Override
    public void reqRead(Rudder rd) {
        multiplexer.reqRead(rd);
    }

    @Override
    public void reqWrite(
            Rudder rd,
            ByteBuffer buf,
            InetSocketAddress adr,
            Object tag,
            DataConsumeListener listener)
            throws IOException {

        BayLog.trace("%s reqWrite: %s", buf);
        multiplexer.reqWrite(rd, buf, adr, tag, listener);
    }

    @Override
    public void reqClose(Rudder rd) {
        closed = true;
        multiplexer.reqClose(rd);
    }


    @Override
    public boolean checkTimeout(Rudder rd, int durationSec) {
        return ship.checkTimeout(durationSec);
    }

    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    @Override
    /**
     * print memory usage
     */
    public synchronized void printUsage(int indent) {
    }

    ////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////
    protected boolean secure() {
        return false;
    }

}
