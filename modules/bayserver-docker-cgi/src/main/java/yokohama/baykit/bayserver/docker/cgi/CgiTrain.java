package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.transporter.InputStreamTransporter;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.train.Train;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.HttpUtil;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.StringTokenizer;

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
        InputStreamTransporter outTp = new InputStreamTransporter(tour.ship.agentId, bufsize);
        CgiStdOutShip outShip = new CgiStdOutShip();
        outShip.init(outIn, tour.ship.agentId, tour, null, handler);
        outTp.init(outIn, new SimpleDataListener(outShip));

        tour.res.setConsumeListener((len, resume) -> {
            if(resume)
                available = true;
        });

        readAll(outIn, outTp);

        // Handle StdErr
        InputStream errIn = handler.process.getErrorStream();
        InputStreamTransporter errTp = new InputStreamTransporter(tour.ship.agentId, bufsize);
        CgiStdErrShip errShip = new CgiStdErrShip();
        errShip.init(errIn, tour.ship.agentId, handler);
        errTp.init(errIn, new SimpleDataListener(errShip));

        readAll(errIn, errTp);

    }

    ///////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////

    private void readAll(InputStream input, ChannelListener lis) {
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
