package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.LifecycleListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Log;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.WritableByteChannelRudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.CharUtil;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;

public class BuiltInLogDocker extends DockerBase implements Log {

    class AgentListener implements LifecycleListener {

        @Override
        public void add(int agentId) {
            String fileName = filePrefix + "_" + agentId + "." + fileExt;
            int size = (int)new File(fileName).length();
            try {
                GrandAgent agt = GrandAgent.get(agentId);
                switch(BayServer.harbor.logMultiplexer()) {
                    case Taxi: {
                        WritableByteChannel output = new FileOutputStream(fileName).getChannel();
                        rudder = new WritableByteChannelRudder(output);
                        multiplexer = agt.taxiMultiplexer;
                        break;
                    }
                    case Spin: {
                        AsynchronousFileChannel ch =
                                AsynchronousFileChannel.open(
                                        Paths.get(fileName),
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE);
                        rudder = new AsynchronousFileChannelRudder(ch);
                        multiplexer = agt.spinMultiplexer;
                        break;
                    }
                    default:
                        throw new Sink("Not supported");
                }

                RudderState st = new RudderState(rudder, new DataListener() {
                    @Override
                    public NextSocketAction notifyConnect() throws IOException {
                        throw new Sink();
                    }

                    @Override
                    public NextSocketAction notifyRead(ByteBuffer buf, InetSocketAddress adr) throws IOException {
                        throw new Sink();
                    }

                    @Override
                    public NextSocketAction notifyEof() {
                        throw new Sink();
                    }

                    @Override
                    public NextSocketAction notifyHandshakeDone(String protocol) throws IOException {
                        throw new Sink();
                    }

                    @Override
                    public boolean notifyProtocolError(ProtocolException e) throws IOException {
                        throw new Sink();
                    }

                    @Override
                    public void notifyError(Throwable e) {
                        BayLog.error(e);
                    }

                    @Override
                    public void notifyClose() {

                    }

                    @Override
                    public boolean checkTimeout(int durationSec) {
                        return false;
                    }
                });
                st.bytesWrote = size;
                multiplexer.addState(rudder, st);
            }
            catch(IOException e) {
                BayLog.fatal(BayMessage.get(Symbol.INT_CANNOT_OPEN_LOG_FILE, fileName));
                BayLog.fatal(e);
            }
        }

        @Override
        public void remove(int agentId) {
            multiplexer.reqClose(rudder);
        }
    }


    /** Mapping table for format */
    static HashMap<String, LogItemFactory> map = new HashMap<>();

    /** Log file name parts */
    String filePrefix;
    String fileExt;

    /** Log format */
    String format;

    /** Log items */
    LogItem[] logItems;

    Rudder rudder;

    /** Multiplexer to write to file */
    Multiplexer multiplexer;

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
            byte[] bytes = StringUtil.toBytes(sb.toString() + CharUtil.LF);
            ByteBuffer buf = ByteBuffer.wrap(bytes, 0, bytes.length);
            multiplexer.reqWrite(
                    rudder, buf, null, "log", null);
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
}
