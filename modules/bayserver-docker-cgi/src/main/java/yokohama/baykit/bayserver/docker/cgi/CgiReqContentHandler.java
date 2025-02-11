package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.RudderStateStore;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Pair;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CgiReqContentHandler implements ReqContentHandler, Runnable {

    final CgiDocker cgiDocker;
    final Tour tour;
    final int tourId;
    boolean available;
    Process process;
    boolean stdOutClosed;
    boolean stdErrClosed;
    long lastAccess;
    Map<String, String> env;
    ArrayList<Pair<byte[], ContentConsumeListener>> buffers = new ArrayList<>();


    public CgiReqContentHandler(CgiDocker cgiDocker, Tour tur, Map<String, String> env) {
        this.tour = tur;
        this.env = env;
        this.tourId = tur.id();
        this.cgiDocker = cgiDocker;
        this.available = false;
        this.stdOutClosed = false;
        this.stdErrClosed = false;
        access();
    }

    ///////////////////////////////////////////////////////////////////
    // implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s CGI:onReadReqContent: len=%d", tur, len);
        if(process != null) {
            writeToStdIn(tur, buf, start, len, lis);
        }
        else {
            // postponed
            buffers.add(new Pair<>(Arrays.copyOfRange(buf, start, len), lis));
        }
        access();
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s CGI:endReqContent", tur);
        access();
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s CGI:abortReq", tur);
        try {
            if(process != null)
                process.getOutputStream().close();
        } catch (IOException e) {
            BayLog.error(e);
        }
        return false;  // not aborted immediately
    }

    ///////////////////////////////////////////////////////////////////
    // Implements Runnable
    ///////////////////////////////////////////////////////////////////


    @Override
    public void run() {
        cgiDocker.subWaitCount();
        BayLog.info("%s challenge postponed tour", tour, cgiDocker.getWaitCount());
        reqStartTour();
    }

    ///////////////////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////////////////

    public void reqStartTour() {
        if(cgiDocker.addProcessCount()) {
            BayLog.info("%s start tour: wait count=%d", tour, cgiDocker.getWaitCount());
            try {
                startTour();

            } catch (IOException e) {
                BayLog.error(e);
                try {
                    tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, "Cannot create process", e);
                }
                catch (IOException ex) {
                    BayLog.error(ex);
                }
                cgiDocker.subProcessCount();
                return;
            }
        }
        else {
            BayLog.warn("%s Cannot start tour: wait count=%d", tour, cgiDocker.getWaitCount());
            GrandAgent agt = GrandAgent.get(tour.ship.agentId);
            agt.addPostpone(this);
        }
        access();
    }

    public void startTour() throws IOException {

        process = cgiDocker.createProcess(env);
        BayLog.debug("%s created process; %s", tour, process);

        // catch up the postponed buffers
        for(Pair<byte[], ContentConsumeListener> pair : buffers) {
            BayLog.debug("%s write postponed data: len=%d", tour, pair.a.length);
            writeToStdIn(tour, pair.a, 0, pair.a.length, pair.b);
        }

        int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();

        ReadableByteChannel outCh = Channels.newChannel(process.getInputStream());
        ReadableByteChannel errCh = Channels.newChannel(process.getErrorStream());
        ChannelRudder outRd = new ReadableByteChannelRudder(outCh);
        ChannelRudder errRd = new ReadableByteChannelRudder(errCh);

        GrandAgent agt = GrandAgent.get(tour.ship.agentId);

        Multiplexer mpx = null;

        switch(BayServer.harbor.cgiMultiplexer()) {
            case Spin: {
                EOFChecker eofChecker = () -> {
                    try {
                        return process.waitFor(0, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        return true;
                    }
                };
                mpx = agt.spinMultiplexer;
                break;
            }

            case Job: {
                mpx = agt.jobMultiplexer;
                break;
            }

            case Taxi: {
                mpx = agt.taxiMultiplexer;
                break;
            }

            case Train:
                CgiTrain tran = new CgiTrain(tour, cgiDocker, this);
                if(!TrainRunner.post(tour.ship.agentId, tran)) {
                    try {
                        tour.res.sendError(tourId, HttpStatus.SERVICE_UNAVAILABLE, "Train is busy");
                    }
                    catch (IOException ex) {
                        BayLog.error(ex);
                    }
                }
                break;

            default:
                throw new Sink();
        }

        if (mpx != null) {
            CgiStdOutShip outShip = new CgiStdOutShip();
            PlainTransporter outTp = new PlainTransporter(agt.netMultiplexer, outShip, false, bufsize, false);
            outTp.init();

            outShip.init(outRd, tour.ship.agentId, tour, outTp, this);

            RudderState outRst = RudderStateStore.getStore(agt.agentId).rent();
            outRst.init(outRd, outTp);
            mpx.addRudderState(outRd, outRst);

            int sipId = outShip.shipId;
            tour.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    outShip.resumeRead(sipId);
                }
            });

            CgiStdErrShip errShip = new CgiStdErrShip();
            PlainTransporter errTp = new PlainTransporter(agt.netMultiplexer, errShip, false, bufsize, false);
            errTp.init();
            errShip.init(errRd, tour.ship.agentId, this);
            RudderState errRst = RudderStateStore.getStore(agt.agentId).rent();
            errRst.init(errRd, errTp);
            mpx.addRudderState(errRd, errRst);

            mpx.reqRead(outRd);
            mpx.reqRead(errRd);
        }
    }

    public void closePipes() {
        try {
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    public void stdOutClosed() {
        stdOutClosed = true;
        if(stdOutClosed && stdErrClosed)
            processFinished();
    }

    public void stdErrClosed() {
        stdErrClosed = true;
        if(stdOutClosed && stdErrClosed)
            processFinished();
    }

    public void access() {
        lastAccess = RoughTime.currentTimeMillis();
    }

    public boolean timedOut() {
        if(cgiDocker.timeoutSec <= 0)
            return false;

        long durationSec = (RoughTime.currentTimeMillis() - lastAccess) / 1000;
        BayLog.debug("%s Check CGI timeout: dur=%d, timeout=%d", tour, durationSec, cgiDocker.timeoutSec);
        return durationSec > cgiDocker.timeoutSec;
    }

    private void writeToStdIn(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        process.getOutputStream().write(buf, start, len);
        process.getOutputStream().flush();
        tur.req.consumed(Tour.TOUR_ID_NOCHECK, len, lis);
    }

    private void processFinished() {
        BayLog.debug("CGI: %s process_finished()", tour);

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            BayLog.error(e);
        }

        int agtId = tour.ship.agentId;

        try {
            process.destroy();
            BayLog.trace(tour + " CGI: process ended");

            int code = process.exitValue();
            BayLog.debug("CGI: Process finished: code=%d", code);

            if (code != 0) {
                // Exec failed
                BayLog.error("CGI: Exec error code=%d", code & 0xff);
                tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, "Exec failed");
            }
            else {
                tour.res.endResContent(tourId);
            }
        }
        catch(IOException e) {
            BayLog.debug(e);
        }

        cgiDocker.subProcessCount();
        if(cgiDocker.getWaitCount() > 0) {
            BayLog.warn("agt#%d Catch up postponed process: process wait count=%d", agtId, cgiDocker.getWaitCount());
            GrandAgent agt = GrandAgent.get(agtId);
            agt.reqCatchUp();
        }
    }
}
