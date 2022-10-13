package baykit.bayserver.docker.cgi;

import baykit.bayserver.*;
import baykit.bayserver.agent.transporter.SpinReadTransporter;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.ParseException;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.Harbor;
import baykit.bayserver.docker.Harbor.FileSendMethod;
import baykit.bayserver.taxi.TaxiRunner;
import baykit.bayserver.tour.ReadFileTaxi;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.base.ClubBase;
import baykit.bayserver.util.CGIUtil;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.StringUtil;
import baykit.bayserver.util.SysUtil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CgiDocker extends ClubBase {

    public static final FileSendMethod DEFAULT_PROC_READ_METHOD = FileSendMethod.Taxi;
    public static final int DEFAULT_TIMEOUT_SEC = 60;

    public String interpreter;
    public String scriptBase;
    public String docRoot;
    public int timeoutSec = DEFAULT_TIMEOUT_SEC;

    /** Method to read stdin/stderr */
    public Harbor.FileSendMethod procReadMethod = DEFAULT_PROC_READ_METHOD;

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(procReadMethod == Harbor.FileSendMethod.Select && !SysUtil.supportSelectPipe()) {
            BayLog.warn(ConfigException.createMessage(CgiMessage.get(CgiSymbol.CGI_PROC_READ_METHOD_SELECT_NOT_SUPPORTED), elm.fileName, elm.lineNo));
            procReadMethod = Harbor.FileSendMethod.Taxi;
        }

        if(procReadMethod == Harbor.FileSendMethod.Spin && !SysUtil.supportNonblockPipeRead()) {
            BayLog.warn(ConfigException.createMessage(CgiMessage.get(CgiSymbol.CGI_PROC_READ_METHOD_SPIN_NOT_SUPPORTED), elm.fileName, elm.lineNo));
            procReadMethod = Harbor.FileSendMethod.Taxi;
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
                        procReadMethod = Harbor.FileSendMethod.Select;
                        break;
                    case "spin":
                        procReadMethod = Harbor.FileSendMethod.Spin;
                        break;
                    case "taxi":
                        procReadMethod = Harbor.FileSendMethod.Taxi;
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
        String fname = "cgi#";

        CgiStdOutYacht outYat = new CgiStdOutYacht();
        CgiStdErrYacht errYat = new CgiStdErrYacht();

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

                SpinReadTransporter outTp = new SpinReadTransporter(bufsize);
                outYat.init(tur, outTp);
                outTp.init(tur.ship.agent.spinHandler, outYat, handler.process.getInputStream(), -1, timeoutSec, ch);
                outTp.openValve();

                SpinReadTransporter errTp = new SpinReadTransporter(bufsize);
                errYat.init(tur);
                errTp.init(tur.ship.agent.spinHandler, errYat, handler.process.getErrorStream(), -1, timeoutSec, ch);
                errTp.openValve();
                break;
            }

            case Taxi:{
                ReadFileTaxi outTxi = new ReadFileTaxi(bufsize);
                outYat.init(tur, outTxi);
                outTxi.init(handler.process.getInputStream(), outYat);
                if(!TaxiRunner.post(outTxi)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                }

                ReadFileTaxi errTxi = new ReadFileTaxi(bufsize);
                errYat.init(tur);
                errTxi.init(handler.process.getErrorStream(), errYat);
                if(!TaxiRunner.post(errTxi)) {
                    throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                }

                break;
            }

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
