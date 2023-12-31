package yokohama.baykit.bayserver.docker.cgi;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.transporter.PlainTransporter;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.agent.transporter.SpinReadTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.ParseException;
import yokohama.baykit.bayserver.common.ReadChannelTaxi;
import yokohama.baykit.bayserver.docker.base.ClubBase;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.CGIUtil;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CgiDocker extends ClubBase {

    enum ProcReadMethod {
        Select,
        Spin,
        Taxi,
        Train
    };

    public static final ProcReadMethod DEFAULT_PROC_READ_METHOD = ProcReadMethod.Taxi;
    public static final int DEFAULT_TIMEOUT_SEC = 60;

    public String interpreter;
    public String scriptBase;
    public String docRoot;
    public int timeoutSec = DEFAULT_TIMEOUT_SEC;

    /** Method to read stdin/stderr */
    public ProcReadMethod procReadMethod = DEFAULT_PROC_READ_METHOD;

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(procReadMethod == ProcReadMethod.Select && !SysUtil.supportSelectPipe()) {
            BayLog.warn(ConfigException.createMessage(CgiMessage.get(CgiSymbol.CGI_PROC_READ_METHOD_SELECT_NOT_SUPPORTED), elm.fileName, elm.lineNo));
            procReadMethod = ProcReadMethod.Taxi;
        }

        if(procReadMethod == ProcReadMethod.Spin && !SysUtil.supportNonblockPipeRead()) {
            BayLog.warn(ConfigException.createMessage(CgiMessage.get(CgiSymbol.CGI_PROC_READ_METHOD_SPIN_NOT_SUPPORTED), elm.fileName, elm.lineNo));
            procReadMethod = ProcReadMethod.Taxi;
        }
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

            case "processreadmethod":
                switch(kv.value.toLowerCase()) {
                    case "select":
                        procReadMethod = ProcReadMethod.Select;
                        break;
                    case "spin":
                        procReadMethod = ProcReadMethod.Spin;
                        break;
                    case "taxi":
                        procReadMethod = ProcReadMethod.Taxi;
                        break;
                    case "train":
                        procReadMethod = ProcReadMethod.Train;
                        break;
                    default:
                        throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
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
        tur.req.setContentHandler(handler);
        handler.startTour(env);

        CgiStdOutShip outShip = new CgiStdOutShip();
        CgiStdErrShip errShip = new CgiStdErrShip();

        Channel outCh = Channels.newChannel(handler.process.getInputStream());
        Channel errCh = Channels.newChannel(handler.process.getErrorStream());

        switch(procReadMethod) {
            case Spin: {
                SpinReadTransporter.EOFChecker ch = () -> {
                    try {
                        return handler.process.waitFor(0, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        return true;
                    }
                };

                GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                SpinReadTransporter outTp = new SpinReadTransporter(bufsize);
                outShip.init(outCh, tur.ship.agentId, tur, outTp, handler);
                outTp.init(
                        agt.spinHandler,
                        new SimpleDataListener(outShip),
                        handler.process.getInputStream(),
                        -1,
                        timeoutSec,
                        ch);

                SpinReadTransporter errTp = new SpinReadTransporter(bufsize);
                errShip.init(errCh, tur.ship.agentId, handler);
                errTp.init(
                        agt.spinHandler,
                        new SimpleDataListener(errShip),
                        handler.process.getErrorStream(),
                        -1,
                        timeoutSec,
                        ch);

                int sipId = tur.ship.shipId;
                tur.res.setConsumeListener((len, resume) -> {
                    if(resume) {
                        outShip.resumeRead(sipId);
                    }
                });
                outTp.openReadValve();
                break;
            }

            case Taxi:{
                PlainTransporter outTp = new PlainTransporter(true, bufsize, false);
                ReadChannelTaxi outTxi = new ReadChannelTaxi(tur.ship.agentId);
                outShip.init(outCh, tur.ship.agentId, tur, outTxi, handler);
                outTxi.setChannelListener(outCh, outTp);
                outTp.init(null, new SimpleDataListener(outShip), outTxi);
                int sipId = tur.ship.shipId;
                tur.res.setConsumeListener((len, resume) -> {
                    if(resume) {
                        outShip.resumeRead(sipId);
                    }
                });

                if(!TaxiRunner.post(tur.ship.agentId, outTxi)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                }


                PlainTransporter errTp = new PlainTransporter(true, bufsize, false);
                ReadChannelTaxi errTxi = new ReadChannelTaxi(tur.ship.agentId);
                errShip.init(errCh, tur.ship.agentId, handler);
                errTxi.setChannelListener(errCh, errTp);
                errTp.init(errCh, new SimpleDataListener(errShip), errTxi);

                if(!TaxiRunner.post(tur.ship.agentId, errTxi)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                }

                break;
            }

            case Train:
                CgiTrain tran = new CgiTrain(tur, this, handler);
                if(!TrainRunner.post(tran)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Train is busy");
                }
                break;

            default:
                throw new Sink();
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
