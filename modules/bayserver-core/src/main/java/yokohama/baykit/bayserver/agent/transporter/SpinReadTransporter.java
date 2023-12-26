package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.SpinHandler;
import yokohama.baykit.bayserver.common.Valve;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class SpinReadTransporter implements SpinHandler.SpinListener, Reusable, Valve {

    public interface EOFChecker {
        boolean isEof();
    }

    SpinHandler spinHandler;
    DataListener dataListener;
    InputStream in;
    ByteBuffer readBuf;
    int totalRead;
    int fileLen;
    int timeoutSec;
    EOFChecker eofChecker;
    boolean isClosed;

    @Override
    public String toString() {
        return "spinRead " + dataListener.toString() + " read=" + totalRead + " len=" + fileLen;
    }

    public SpinReadTransporter(int bufsize) {
        this.readBuf = ByteBuffer.allocate(bufsize);
    }


    public void init(SpinHandler spnHnd, DataListener lis, InputStream in, int limit, int timeoutSec, EOFChecker chk) {
        this.spinHandler = spnHnd;
        this.dataListener = lis;
        this.in = in;
        this.fileLen = limit;
        this.totalRead = 0;
        this.timeoutSec = timeoutSec;
        this.eofChecker = chk;
        this.isClosed = false;
    }


    ////////////////////////////////////////////////////////////////////
    // implements Reusable
    ////////////////////////////////////////////////////////////////////

    public void reset() {
        dataListener = null;
        in = null;
    }


    ////////////////////////////////////////////////////////////////////
    // implements SpinListener
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction lap(boolean[] spun) {
        spun[0] = false;

        try {
            boolean eof = false;
            int len = in.available();
            if(len == 0) {
                if(eofChecker != null)
                    eof = eofChecker.isEof();

                if(!eof) {
                    //BayLog.debug("%s Spin read: No stream data", this);
                    spun[0] = true;
                    return NextSocketAction.Continue;
                }
                else {
                    BayLog.debug("%s Spin read: EOF\\(^o^)/", this);
                }
            }

            if(!eof) {
                if (len > readBuf.capacity())
                    len = readBuf.capacity();

                readBuf.clear();
                int readLen = in.read(readBuf.array(), 0, len);
                if (readLen == -1) {
                    eof = true;
                }
                else {
                    readBuf.limit(readLen);
                    totalRead += readLen;
                }
            }

            if(!eof) {
                NextSocketAction act = dataListener.notifyRead(readBuf, null);

                //BayLog.debug("totalRead=%d fileLen=%d", totalRead, fileLen);
                if (fileLen == -1 || totalRead < fileLen) {
                    return act;
                }
            }

            // EOF
            //BayLog.error("%s EOF", this);
            dataListener.notifyEof();
            close();
            return NextSocketAction.Close;
        }
        catch(Exception e) {
            BayLog.error(e, "%s Error", this);
            close();
            return NextSocketAction.Close;
        }
    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return durationSec > timeoutSec;
    }

    @Override
    public void close() {
        //BayLog.error("%s close", this);
        if(in != null) {
            try {
                in.close();
            } catch (IOException e) {
            }
        }
        dataListener.notifyClose();
        isClosed = true;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Valve
    ////////////////////////////////////////////////////////////////////

    @Override
    public void openValve() {
        if(!isClosed)
            spinHandler.askToCallBack(this);
    }

    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////


}
