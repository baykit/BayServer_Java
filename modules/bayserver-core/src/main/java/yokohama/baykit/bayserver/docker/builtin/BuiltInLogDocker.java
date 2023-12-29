package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.common.WriteStreamShip;
import yokohama.baykit.bayserver.common.WriteStreamTaxi;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Log;
import yokohama.baykit.bayserver.common.docker.DockerBase;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BuiltInLogDocker extends DockerBase implements Log {

    class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            String fileName = filePrefix + "_" + agentId + "." + fileExt;
            WriteStreamShip lsip = new WriteStreamShip();
            try {
                WriteStreamTaxi txi = new WriteStreamTaxi();
                lsip.init(agentId, txi);
                txi.init(agentId, new FileOutputStream(fileName));
            }
            catch(IOException e) {
                BayLog.fatal(BayMessage.get(Symbol.INT_CANNOT_OPEN_LOG_FILE, fileName));
                BayLog.fatal(e);
            }
            loggers.put(agentId, lsip);
        }

        @Override
        public void remove(int agentId) {
            loggers.remove(agentId);
        }
    }

    enum LogWriteMethod {
        Select,
        Spin,
        Taxi
    }
    public static LogWriteMethod DEFAULT_LOG_WRITE_METHOD = LogWriteMethod.Taxi;

    /** Mapping table for format */
    static HashMap<String, LogItemFactory> map = new HashMap<>();

    /** Log file name parts */
    String filePrefix;
    String fileExt;

    /**
     *  Logger for each agent.
     *  Map of Agent ID => LogBoat
     */
    Map<Integer, WriteStreamShip> loggers = new HashMap<>();

    /** Log format */
    String format;

    /** Log items */
    LogItem[] logItems;

    /** Log write method */
    LogWriteMethod logWriteMethod = DEFAULT_LOG_WRITE_METHOD;

    static String lineSep = System.getProperty("line.separator");

    static {
        // Create mapping table
        map.put("a", LogItems.RemoteIpItem.factory);
        map.put("A", LogItems.ServerIpItem.factory);
        map.put("b", LogItems.RequestBytesItem2.factory);
        map.put("B", LogItems.RequestBytesItem1.factory);
        map.put("c", LogItems.ConnectionStatusItem.factory);
        map.put("e", LogItems.NullItem.factory);
        map.put("h", LogItems.RemoteHostItem.factory);
        map.put("H", LogItems.ProtocolItem.factory);
        map.put("i", LogItems.RequestHeaderItem.factory);
        map.put("l", LogItems.RemoteLogItem.factory);
        map.put("m", LogItems.MethodItem.factory);
        map.put("n", LogItems.NullItem.factory);
        map.put("o", LogItems.ResponseHeaderItem.factory);
        map.put("p", LogItems.PortItem.factory);
        map.put("P", LogItems.NullItem.factory);
        map.put("q", LogItems.QueryStringItem.factory);
        map.put("r", LogItems.StartLineItem.factory);
        map.put("s", LogItems.StatusItem.factory);
        map.put(">s", LogItems.StatusItem.factory);
        map.put("t", LogItems.TimeItem.factory);
        map.put("T", LogItems.IntervalItem.factory);
        map.put("u", LogItems.RemoteUserItem.factory);
        map.put("U", LogItems.RequestUrlItem.factory);
        map.put("v", LogItems.ServerNameItem.factory);
        map.put("V", LogItems.NullItem.factory);
    }
    
    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
        int p = elm.arg.lastIndexOf('.');
        if(p == -1) {
            this.filePrefix = elm.arg;
            this.fileExt = "";
        }
        else {
            this.filePrefix = elm.arg.substring(0, p);
            this.fileExt = elm.arg.substring(p + 1);
        }

        if(format == null) {
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    BayMessage.get(
                            Symbol.CFG_INVALID_LOG_FORMAT,
                            ""));
        }

        if(!new File(filePrefix).isAbsolute())
            filePrefix = new File(BayServer.bservHome, filePrefix).getPath();
        File logFile = new File(filePrefix);
        File logDir = logFile.getParentFile();
        if(!logDir.isDirectory())
            logDir.mkdirs();

        // Parse format
        ArrayList<LogItem> list = new ArrayList<>();
        compile(format, list, elm.fileName, elm.lineNo);
        logItems = list.toArray(new LogItem[0]);

        // Check log write method
        if(logWriteMethod == LogWriteMethod.Select && !SysUtil.supportSelectFile()) {
            BayLog.warn(BayMessage.get(Symbol.CFG_LOG_WRITE_METHOD_SELECT_NOT_SUPPORTED));
            logWriteMethod = LogWriteMethod.Taxi;
        }

        if(logWriteMethod == LogWriteMethod.Spin && !SysUtil.supportNonblockFileWrite()) {
            BayLog.warn(BayMessage.get(Symbol.CFG_LOG_WRITE_METHOD_SPIN_NOT_SUPPORTED));
            logWriteMethod = LogWriteMethod.Taxi;
        }

        GrandAgent.addLifecycleListener(new AgentListener());
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return false;

            case "format":
                format = kv.value;
                break;

            case "logwritemethod":
                switch(kv.value.toLowerCase()) {
                    case "select":
                        logWriteMethod = LogWriteMethod.Select;
                        break;
                    case "spin":
                        logWriteMethod = LogWriteMethod.Spin;
                        break;
                    case "taxi":
                        logWriteMethod = LogWriteMethod.Taxi;
                        break;
                    default:
                        throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PARAMETER_VALUE(kv.value));
                }
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////
    //  Implements Log                                                           //
    ///////////////////////////////////////////////////////////////////////////////

    @Override
    public void log(Tour tour) throws IOException {

        StringBuilder sb = new StringBuilder();
        for (LogItem logItem : logItems) {
            String item = logItem.getItem(tour);
            if (item == null)
                sb.append("-");
            else
                sb.append(item);
        }

        // If threre are message to write, write it
        if (sb.length() > 0) {
            getLogger(tour.ship.agentId).log(sb.toString());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    //  Custom methods                                                           //
    ///////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////
    //  Private methods                                                          //
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Compile format pattern
     */
    void compile(String str, ArrayList<LogItem> items, String fileName, int lineNo) throws ConfigException {

        // Find control code
        int pos = str.indexOf('%');
        if (pos != -1) {
            String text = str.substring(0, pos);
            items.add(new LogItems.TextItem(text));
            compileCtl(str.substring(pos + 1), items, fileName, lineNo);
        } else {
            items.add(new LogItems.TextItem(str));
        }
    }

    /**
     * Compile format pattern(Control code)
     */
    void compileCtl(String str, ArrayList<LogItem> items, String fileName, int lineNo) throws ConfigException {

        String param = null;

        // if exists param
        if (str.charAt(0) == '{') {
            // find close bracket
            int pos = str.indexOf('}');
            if (pos == -1) {
                throw new ConfigException(fileName, lineNo, BayMessage.CFG_INVALID_LOG_FORMAT(format));
            }
            param = str.substring(1, pos);
            str = str.substring(pos + 1);
        }

        String ctlChar = "";
        boolean error = false;

        if (str.length() == 0)
            error = true;

        if (!error) {
            // get control char
            ctlChar = str.substring(0, 1);
            str = str.substring(1);

            if (ctlChar.equals(">")) {
                if (str.length() == 0) {
                    error = true;
                } else {
                    ctlChar = str.substring(0, 1);
                    str = str.substring(1);
                }
            }
        }

        LogItemFactory fct = null;
        if (!error) {
            fct = map.get(ctlChar);
            if (fct == null)
                error = true;
        }

        if (error) {
            throw new ConfigException(
                    fileName,
                    lineNo,
                    BayMessage.CFG_INVALID_LOG_FORMAT(format + " (unknown control code: '%" + ctlChar + "')"));
        }

        LogItem item = fct.newLogItem();
        item.init(param);
        items.add(item);
        compile(str, items, fileName, lineNo);
    }

    private WriteStreamShip getLogger(int agentId) {
        return loggers.get(agentId);
    }
}
