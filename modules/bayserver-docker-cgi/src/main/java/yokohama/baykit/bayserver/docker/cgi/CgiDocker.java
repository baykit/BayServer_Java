package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.common.EOFChecker;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.SimpleDataListener;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.ClubBase;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.CGIUtil;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CgiDocker extends ClubBase {

    public static final int DEFAULT_TIMEOUT_SEC = 60;

    public String interpreter;
    public String scriptBase;
    public String docRoot;
    public int timeoutSec = DEFAULT_TIMEOUT_SEC;


    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);


    }
    

    ///////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "interpreter":
                interpreter = kv.value;
                break;

            case "scriptase":
                scriptBase = kv.value;
                break;

            case "docroot":
                docRoot = kv.value;
                break;

            case "timeout":
                timeoutSec = Integer.parseInt(kv.value);
                break;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements Club
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void arrive(Tour tur) throws HttpException {
        if (tur.req.uri.contains("..")) {
            throw new HttpException(HttpStatus.FORBIDDEN, tur.req.uri);
        }

        String base = scriptBase;
        if(base == null)
            base = tur.town.location();

        if(StringUtil.empty(base)) {
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, tur.town + " scriptBase of cgi docker or location of town is not specified.");
        }

        String root = docRoot;
        if(root == null)
            root = tur.town.location();

        if(StringUtil.empty(root)) {
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, tur.town + " docRoot of cgi docker or location of town is not specified.");
        }

        Map<String, String> env = CGIUtil.getEnv(tur.town.name(), root, base, tur);
        if (BayServer.harbor.traceHeader()) {
            env.forEach((name, value) -> BayLog.info("%s cgi: env: %s=%s", tur, name, value));
        }

        String fileName = env.get(CGIUtil.SCRIPT_FILENAME);
        if (!new File(fileName).isFile()) {
            throw new HttpException(HttpStatus.NOT_FOUND, fileName);
        }

        int bufsize = tur.ship.protocolHandler.maxResPacketDataSize();
        CgiReqContentHandler handler = new CgiReqContentHandler(this, tur);
        tur.req.setReqContentHandler(handler);
        handler.startTour(env);

        CgiStdOutShip outShip = new CgiStdOutShip();
        CgiStdErrShip errShip = new CgiStdErrShip();

        ReadableByteChannel outCh = Channels.newChannel(handler.process.getInputStream());
        ReadableByteChannel errCh = Channels.newChannel(handler.process.getErrorStream());
        ChannelRudder outRd = new ReadableByteChannelRudder(outCh);
        ChannelRudder errRd = new ReadableByteChannelRudder(errCh);

        GrandAgent agt = GrandAgent.get(tur.ship.agentId);

        Multiplexer mpx = null;

        switch(BayServer.harbor.cgiMultiplexer()) {
            case Spin: {
                EOFChecker eofChecker = () -> {
                    try {
                        return handler.process.waitFor(0, TimeUnit.MILLISECONDS);
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
                CgiTrain tran = new CgiTrain(tur, this, handler);
                if(!TrainRunner.post(tur.ship.agentId, tran)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Train is busy");
                }
                break;

            default:
                throw new Sink();
        }

        if (mpx != null) {
            PlainTransporter outTp = new PlainTransporter(false, bufsize);
            outShip.init(outRd, tur.ship.agentId, tur, mpx, handler);

            mpx.addState(
                    outRd,
                    new RudderState(
                            outRd,
                            new SimpleDataListener(outShip),
                            outTp));

            int sipId = tur.ship.shipId;
            tur.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    outShip.resumeRead(sipId);
                }
            });

            PlainTransporter errTp = new PlainTransporter(false, bufsize);
            errShip.init(errRd, tur.ship.agentId, handler);
            mpx.addState(
                    errRd,
                    new RudderState(
                            errRd,
                            new SimpleDataListener(errShip),
                            errTp));

            mpx.reqRead(outRd);
            mpx.reqRead(errRd);
        }
    }


    ///////////////////////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////////////////////

    protected Process createProcess(Map<String, String> env)  throws IOException {
        ProcessBuilder pb;
        String script = env.get(CGIUtil.SCRIPT_FILENAME);
        if(interpreter == null) {
            pb = new ProcessBuilder(script);
        }
        else {
            pb = new ProcessBuilder(interpreter, script);
        }

        pb.environment().clear();
        pb.environment().putAll(env);
        return pb.start();
    }

    static {
        try {
            CgiMessage.init();
        } catch (ParseException e) {
            BayLog.error(e);
        }
    }
}
