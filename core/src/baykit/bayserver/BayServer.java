package baykit.bayserver;

import baykit.bayserver.agent.GrandAgent;
import baykit.bayserver.agent.GrandAgentMonitor;
import baykit.bayserver.agent.signal.SignalAgent;
import baykit.bayserver.agent.signal.SignalSender;
import baykit.bayserver.docker.*;
import baykit.bayserver.taxi.TaxiRunner;
import baykit.bayserver.train.TrainRunner;
import baykit.bayserver.protocol.PacketStore;
import baykit.bayserver.protocol.ProtocolHandlerStore;
import baykit.bayserver.docker.base.InboundShipStore;
import baykit.bayserver.tour.TourStore;
import baykit.bayserver.bcf.*;
import baykit.bayserver.docker.builtin.BuiltInHarborDocker;
import baykit.bayserver.util.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BayServer {

    private static final String RESOURCE_NAME = "baykit/bayserver/version";

    public static final String ENV_BAYSERVER_HOME = "BSERV_HOME";
    public static final String ENV_BAYSERVER_PLAN = "BSERV_PLAN";

    /** Host name */
    public static String myHostName;

    /** Host address */
    public static String myHostAddr;

    /** BSERV_HOME directory */
    public static String bservHome;

    /** Configuration file name (full path) */
    public static String bservPlan;

    /** Configuration file directory name (full path) */
    public static String planDir;

    /** Version */
    private static String version;

    /** default session timeout */
    private static int defaultSessionTimeout = Constants.DEFAULT_SESSION_TIMEOUT;

    /** max header length */
    private static int maxHeaderLength = Constants.DEFAULT_MAX_HEADER_LENGTH;

    /** max header count */
    private static int maxHeaderCount = Constants.DEFAULT_MAX_HEADER_COUNT;

    /** max content length */
    private static int maxContentLength = Constants.DEFAULT_MAX_CONTENT_LENGTH;

    /** Socket timeout */
    private static int socketTimeout = Constants.DEFAULT_SOCKET_TIMEOUT;

    /** Decode tilde */
    public static boolean decodeTilde = Constants.DEFAULT_DECODE_TILDE;

    private static String softwareName;

    /** Http 1.1 supported */
    private static boolean http11Supported = true;

    /** Dump thread on exit */
    private static boolean dumpOnExit = Constants.DEFAULT_DUMP_ON_EXIT;

    /** Consume request buffer */
    private static boolean consumeRequest = Constants.DEFAULT_CONSUME_REQUEST;

    public static Cities cities = new Cities();

    /** Port dockers */
    public static List<Port> ports = new ArrayList<>();

    /** Harbor docker */
    public static Harbor harbor;

    /** BayAgent */
    public static SignalAgent signalAgent;


    /**
     * Date format for debug
     */
    private static SimpleDateFormat formatter = new SimpleDateFormat(
            "[yyyy/MM/dd HH:mm:ss] ");

    /**
     * Can not instantiate BayServer class
     */
    private BayServer(){}

    static {
        ResourceBundle bundle = ResourceBundle.getBundle(RESOURCE_NAME);
        version = bundle.getString("version");
    }

    ////////////////////////////////////////////////////////////////
    // public methods
    ////////////////////////////////////////////////////////////////
    public static void main(String[] args) throws Exception {
        String cmd = null;
        String home = System.getenv(ENV_BAYSERVER_HOME);
        String plan = System.getenv(ENV_BAYSERVER_PLAN);
        String mkpass = null;

        for (String arg : args) {
            if (arg.equalsIgnoreCase("-start"))
                cmd = null;

            else if (arg.equalsIgnoreCase("-stop") || arg.equalsIgnoreCase("-shutdown"))
                cmd = SignalAgent.COMMAND_SHUTDOWN;

            else if (arg.equalsIgnoreCase("-restartAgents"))
                cmd = SignalAgent.COMMAND_RESTART_AGENTS;

            else if (arg.equalsIgnoreCase("-reloadCert"))
                cmd = SignalAgent.COMMAND_RELOAD_CERT;

            else if (arg.equalsIgnoreCase("-memUsage"))
                cmd = SignalAgent.COMMAND_MEM_USAGE;

            else if (arg.equalsIgnoreCase("-abort"))
                cmd = SignalAgent.COMMAND_ABORT;

            else if (arg.toLowerCase().startsWith("-home="))
                home = arg.substring(6);

            else if (arg.toLowerCase().startsWith("-plan="))
                plan = arg.substring(6);

            else if (arg.toLowerCase().startsWith("-mkpass="))
                mkpass = arg.substring(8);

            else if (arg.toLowerCase().startsWith("-loglevel=")) {
                BayLog.setLogLevel(arg.substring(10));
            }
        }

        if(mkpass != null) {
            System.out.println(MD5Password.encode(mkpass));
            return;
        }
        
        BayServer.init(home, plan);

        if(cmd == null) {
            BayServer.start();
        }
        else {
            new SignalSender().sendCommand(cmd);
        }
    }

    public static void init(String home, String plan) throws BayException, IOException {

        // Get debug mode
        BayLog.debug("Log level=" + BayLog.logLevel);
        
        // Get BayServer home
        if(home == null)
            home = ".";
        bservHome = new File(home).getAbsoluteFile().getCanonicalPath();
        if(!new File(bservHome).isDirectory())
            throw new BayException("BayServer home is not a directory: " + bservHome);
        if (bservHome.endsWith(File.separator))
            bservHome = bservHome.substring(0, bservHome.length() - 1);
        BayLog.info("BayServer home: " + bservHome);


        // Get plan file
        if(plan == null)
            plan = "plan/bayserver.plan";
        if(!new File(plan).isAbsolute())
            plan = bservHome + "/" + plan;
        bservPlan = new File(plan).getAbsoluteFile().getCanonicalPath();
        BayLog.info("BayServer Plan: " + bservPlan);
        if(!new File(bservPlan).isFile())
            throw new BayException("Plan file is not a file: " + bservPlan);

        planDir = new File(bservHome, "plan").getAbsolutePath();
    }


    /**
     * Start the system
     */
    public static void start() {
        try {
            BayMessage.init(bservHome + "/lib/conf/messages", Locale.getDefault());
            BayDockers.init(bservHome + "/lib/conf/dockers.bcf");
            Mimes.init(bservHome + "/lib/conf/mimes.bcf");
            HttpStatus.init(bservHome + "/lib/conf/httpstatus.bcf");
            loadPlan(bservPlan);

            String redirectFile = harbor.redirectFile();
            if(redirectFile != null) {
                if(!new File(redirectFile).isAbsolute())
                    redirectFile = BayServer.bservHome + "/" + redirectFile;
                PrintStream os = new PrintStream(new FileOutputStream(redirectFile, true));
                System.setOut(os);
                System.setErr(os);
            }

            printVersion();

            if(ports.size() == 0) {
                throw new BayException(BayMessage.get(Symbol.CFG_NO_PORT_DOCKER));
            }
                
            try {
                InetAddress local = InetAddress.getLocalHost();
                myHostName = local.getHostName();
                myHostAddr = local.getHostAddress();
            } catch (UnknownHostException e) {
                myHostName = "localhost";
                myHostAddr = "127.0.0.1";
            }

            BayLog.debug("Host name    : " + myHostName);
            BayLog.debug("Host address : " + myHostAddr);

            Map<ServerSocketChannel, Port> anchoredPortMap = new HashMap<>();   // TCP server port map
            Map<DatagramChannel, Port> unanchoredPortMap = new HashMap<>();    // UDB server port map
            for (Port portDkr : ports) {
                // Open TCP port
                SocketAddress adr = portDkr.address();

                if(portDkr.anchored()) {
                    BayLog.info(BayMessage.get(Symbol.MSG_OPENING_TCP_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), portDkr.protocol()));
                    ServerSocketChannel ch;
                    if(adr instanceof InetSocketAddress)
                        ch = ServerSocketChannel.open();
                    else {
                        File f = new File(portDkr.socketPath());
                        if(f.exists())
                            f.delete();
                        ch = SysUtil.openUnixDomainServerSocketChannel();
                    }

                    ch.configureBlocking(false);
                    try {
                        ch.bind(adr);
                    } catch (SocketException e) {
                        BayLog.error(BayMessage.get(Symbol.INT_CANNOT_OPEN_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), e.getMessage()));
                        return;
                    }
                    anchoredPortMap.put(ch, portDkr);
                }
                else {
                    BayLog.info(BayMessage.get(Symbol.MSG_OPENING_UDP_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), portDkr.protocol()));
                    DatagramChannel ch = DatagramChannel.open();
                    ch.configureBlocking(false);
                    try {
                        ch.bind(adr);
                    } catch (SocketException e) {
                        BayLog.error(BayMessage.get(Symbol.INT_CANNOT_OPEN_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), e.getMessage()));
                        return;
                    }
                    unanchoredPortMap.put(ch, portDkr);
                }
            }

            /** Init stores, memory usage managers */
            PacketStore.init();
            InboundShipStore.init();
            ProtocolHandlerStore.init();
            TourStore.init(TourStore.MAX_TOURS);
            TrainRunner.init(harbor.trainRunners());
            TaxiRunner.init(harbor.taxiRunners());
            MemUsage.init();
            SignalAgent.init(harbor.controlPort());
            GrandAgent.init(harbor.grandAgents(), anchoredPortMap, unanchoredPortMap, harbor.maxShips());

            createPidFile(SysUtil.pid());

            while(!GrandAgent.monitors.isEmpty()) {
                Selector sel = Selector.open();
                HashMap<SelectableChannel, GrandAgentMonitor> pipToMonMap = new HashMap<>();
                for(GrandAgentMonitor mon : GrandAgent.monitors) {
                    BayLog.debug("Monitoring pipe of %s", mon);
                    mon.recvPipe.source().configureBlocking(false);
                    mon.recvPipe.source().register(sel, SelectionKey.OP_READ);
                    pipToMonMap.put(mon.recvPipe.source(), mon);
                }

                ServerSocketChannel serverSkt = null;
                if (SignalAgent.signalAgent != null) {
                    serverSkt = SignalAgent.signalAgent.serverSocket;
                    serverSkt.register(sel, SelectionKey.OP_ACCEPT);
                }

                int neys = sel.select();

                for(SelectionKey key: sel.selectedKeys()) {
                    if (key.channel() == serverSkt) {
                        SignalAgent.signalAgent.onSocketReadable();
                    }
                    else {
                        GrandAgentMonitor mon = pipToMonMap.get(key.channel());
                        mon.onReadable();
                    }
                }

                sel.close();
            }

            SignalAgent.term();
            System.exit(0);
        } catch (Throwable e) {
            BayLog.error(e);
            System.exit(1);
        }
    }

    /**
     * Get the BayServer version
     */
    public static String getVersion() {
        return Version.VERSION;
    }

    /**
     * Get the software name.
     */
    public static String getSoftwareName() {
        if (softwareName == null)
            softwareName = "BayServer/" + getVersion();
        return softwareName;
    }


    public static City findCity(String name) {
        return cities.findCity(name);
    }

    public static String parsePath(String location) throws FileNotFoundException {
        location = getLocation(location);

        if(!new File(location).exists())
            throw new FileNotFoundException(location);

        return location;
    }

    public static String getLocation(String location) {
        if(!new File(location).isAbsolute())
            return bservHome + File.separator + location;
        else
            return location;
    }


    ////////////////////////////////////////////////////////////////
    // private methods
    ////////////////////////////////////////////////////////////////
    private static void loadPlan(String bservConf) throws BayException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(bservConf);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();
        for(BcfObject o: doc.contentList) {
            if(o instanceof BcfElement) {
                Docker dkr = BayDockers.createDocker((BcfElement)o, null);
                if(dkr instanceof Port) {
                    ports.add((Port)dkr);
                }
                else if(dkr instanceof Harbor) {
                    harbor = (BuiltInHarborDocker)dkr;
                }
                else if(dkr instanceof City) {
                    cities.add((City)dkr);
                }
            }
        }
    }

    /**
     * Print version information
     */
    private static void printVersion() {

        String version = "Version " + getVersion();
        while (version.length() < 28)
            version = ' ' + version;

        System.out.println("        ----------------------");
        System.out.println("       /     BayServer        \\");
        System.out.println("-----------------------------------------------------");
        System.out.print(" \\");
        for(int i = 0; i < 47 - version.length(); i++)
            System.out.print(" ");
        System.out.println(version + "  /");
        System.out.println("  \\           Copyright (C) 2000 Yokohama Baykit  /");
        System.out.println("   \\                     http://baykit.yokohama  /");
        System.out.println("    ---------------------------------------------");
    }


    private static void createPidFile(long pid) throws IOException {

        try(FileOutputStream os = new FileOutputStream(getLocation(harbor.pidFile()))) {
            OutputStreamWriter w = new OutputStreamWriter(os);
            w.write(Long.toString(pid));
            w.flush();
        }
    }

}