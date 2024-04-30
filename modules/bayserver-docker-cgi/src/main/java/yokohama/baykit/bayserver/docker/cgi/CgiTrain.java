package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.SimpleDataListener;
import yokohama.baykit.bayserver.rudder.InputStreamRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class CgiTrain extends Train {

    final CgiDocker cgiDocker;
    CgiReqContentHandler handler;
    Tour tour;
    boolean available;

    public CgiTrain(Tour tur, CgiDocker cgiDocker, CgiReqContentHandler handler) {
        this.cgiDocker = cgiDocker;
        this.handler = handler;
        this.tour = tur;
        this.available = true;
    }

    ///////////////////////////////////////////////////////////////////
    // implements Runnable
    ///////////////////////////////////////////////////////////////////

    @Override
    public void depart() {

        GrandAgent agt = GrandAgent.get(tour.ship.agentId);

        // Handle StdOut
        int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
        InputStream outIn = handler.process.getInputStream();
        Rudder outRd = new InputStreamRudder(outIn);

        CgiStdOutShip outShip = new CgiStdOutShip();
        PlainTransporter outTp =
                new PlainTransporter(
                        agt.netMultiplexer,
                        outShip,
                        true,
                        bufsize,
                        false);

        outShip.init(outRd, tour.ship.agentId, tour, null, handler);
        outTp.init();

        tour.res.setConsumeListener((len, resume) -> {
            if(resume)
                available = true;
        });

        ByteBuffer buffer = ByteBuffer.allocate(bufsize);
        readAll(outRd, new SimpleDataListener(outShip), buffer);
        BayLog.debug("%s Stdout done", this);

        // Handle StdErr
        InputStream errIn = handler.process.getErrorStream();
        Rudder errRd = new InputStreamRudder(errIn);
        CgiStdErrShip errShip = new CgiStdErrShip();

        errShip.init(errRd, tour.ship.agentId, handler);

        readAll(errRd, new SimpleDataListener(errShip), buffer);
        BayLog.debug("%s Stderr done", this);
    }

    @Override
    protected void onTimer() {
        BayLog.debug("%s onTimer: %s", this, tour);
        if (handler.timedOut()) {
            // Kill cgi process instead of handing timeout
            BayLog.warn("%s Kill process!: %s", tour, handler.process);
            handler.process.destroyForcibly();
        }
    }


    ///////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////

    private void readAll(Rudder rd, DataListener lis, ByteBuffer buf) {
        while_break:
        try {
            while (true) {
                buf.clear();
                InputStream input = InputStreamRudder.getInputStream(rd);
                int len = input.read(buf.array());
                if (len == -1)
                    len = 0;
                NextSocketAction act;
                if(len == 0) {
                    act = lis.notifyEof();
                }
                else {
                    buf.limit(len);
                    act = lis.notifyRead(buf, null);
                }

                switch (act) {
                    case Continue:
                    case Suspend:
                        try {
                            while (!available) {
                                Thread.sleep(100);
                            }
                        } catch (InterruptedException e) {
                            BayLog.error(e);
                            throw new Sink(e.getMessage());
                        }
                        continue;

                    case Close:
                        break while_break;

                    default:
                        throw new Sink();
                }
            }
        }
        catch(IOException e) {
            BayLog.error(e);
        }

        try {
            rd.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
        lis.notifyClose();
    }

}
