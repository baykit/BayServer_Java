package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.PlainTransporter;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;

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

        // Handle StdOut
        int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
        InputStream outIn = handler.process.getInputStream();
        Channel outCh = Channels.newChannel(outIn);
        PlainTransporter outTp = new PlainTransporter(true, bufsize, false);
        CgiStdOutShip outShip = new CgiStdOutShip();
        outShip.init(outCh, tour.ship.agentId, tour, null, handler);
        outTp.init(null, outCh, new SimpleDataListener(outShip));

        tour.res.setConsumeListener((len, resume) -> {
            if(resume)
                available = true;
        });

        readAll(outCh, outTp);

        // Handle StdErr
        InputStream errIn = handler.process.getErrorStream();
        Channel errCh = Channels.newChannel(errIn);
        CgiStdErrShip errShip = new CgiStdErrShip();
        PlainTransporter errTp = new PlainTransporter(false, bufsize, false);

        errTp.init(null, errCh, new SimpleDataListener(errShip));

        errShip.init(errCh, tour.ship.agentId, handler);

        readAll(errCh, errTp);

    }

    ///////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////

    private void readAll(Channel input, ChannelListener lis) {
        while_break:
        try {
            while (true) {
                NextSocketAction act = lis.onReadable(input);
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
            lis.onError(input, e);
        }

        try {
            input.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
        lis.onClosed(input);
    }
}
