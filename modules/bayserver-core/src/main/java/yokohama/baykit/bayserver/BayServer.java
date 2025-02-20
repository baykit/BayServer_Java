package yokohama.baykit.bayserver;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.monitor.GrandAgentMonitor;
import yokohama.baykit.bayserver.agent.signal.SignalAgent;
import yokohama.baykit.bayserver.agent.signal.SignalSender;
import yokohama.baykit.bayserver.common.*;
import yokohama.baykit.bayserver.rudder.*;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerStore;
import yokohama.baykit.bayserver.tour.TourStore;
import yokohama.baykit.bayserver.docker.builtin.BuiltInHarborDocker;
import yokohama.baykit.bayserver.util.*;
import yokohama.baykit.bayserver.bcf.BcfDocument;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfObject;
import yokohama.baykit.bayserver.bcf.BcfParser;
import yokohama.baykit.bayserver.docker.City;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Harbor;
import yokohama.baykit.bayserver.docker.Port;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.IntStream;

public class BayServer {

    public static final String ENV_BAYSERVER_HOME = "BSERV_HOME";
    public static final String ENV_BAYSERVER_PLAN = "BSERV_PLAN";

    /**
     * Defualt decode tilde
     */
    public static final boolean DEFAULT_DECODE_TILDE = false;

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

    /** Decode tilde */
    public static boolean decodeTilde = DEFAULT_DECODE_TILDE;

    private static String softwareName;

    public static Cities cities = new Cities();

    /** Port dockers */
    public static List<Port> ports = new ArrayList<>();

    /** Harbor docker */
    public static Harbor harbor;

    /** BayAgent */
    public static SignalAgent signalAgent;

    public static final ArrayList<Pair<Rudder, Port>> anchorablePorts = new ArrayList<>();

    public static final ArrayList<Pair<Rudder, Port>> unanchorablePorts = new ArrayList<>();

    /**
     * Date format for debug
     */
    private static SimpleDateFormat formatter = new SimpleDateFormat(
            "[yyyy/MM/dd HH:mm:ss] ");

    /**
     * Can not instantiate BayServer class
     */
    private BayServer(){}

    ////////////////////////////////////////////////////////////////
    // public methods
    ////////////////////////////////////////////////////////////////
    public static void main(String[] args) throws Exception {
        String cmd = null;
        String home = System.getenv(ENV_BAYSERVER_HOME);
        String plan = System.getenv(ENV_BAYSERVER_PLAN);
        String mkpass = null;
        boolean init = false;

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

            else if (arg.equalsIgnoreCase("-init"))
                init = true;

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

        BayLog.debug("Class libralies:");
        ClassLoader classLoader = BayServer.class.getClassLoader();
        if(classLoader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            for (int i = 0; i < urls.length; i++) {
                BayLog.debug(" Path[%d]: %s", i + 1, urls[i].getFile());
            }
        }

        // Get debug mode
        BayLog.debug("Log level=" + BayLog.logLevel);

        getHome(home);
        if(init) {
            init();
        }
        else {
            getPlan(plan);

            if (cmd == null) {
                BayServer.start();
            } else {
                new SignalSender().sendCommand(cmd);
            }
        }
    }

    /**
     * Start the system
     */
    public static void start() {
        try {
            RoughTime.init();
            BayMessage.init("/conf/messages", Locale.getDefault());
            BayDockers.init("/conf/dockers.bcf");
            Mimes.init("/conf/mimes.bcf");
            HttpStatus.init("/conf/httpstatus.bcf");
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

            /** Init stores, memory usage managers */
            PacketStore.init();
            InboundShipStore.init();
            ProtocolHandlerStore.init();
            TourStore.init(TourStore.MAX_TOURS);
            MemUsage.init();

            openPorts();

            GrandAgent.init(
                    IntStream.rangeClosed(1, harbor.grandAgents()).toArray(),
                    harbor.maxShips());

            invokeRunners();

            GrandAgentMonitor.init(harbor.grandAgents());
            SignalAgent.init(harbor.controlPort());
            createPidFile(SysUtil.pid());

        } catch (Throwable e) {
            BayLog.fatal(e);
            System.exit(1);
        }
    }

    public static void openPorts() throws IOException {

        for (Port portDkr : ports) {
            // Open TCP port
            SocketAddress adr = portDkr.address();

            if(portDkr.anchored()) {
                BayLog.info(BayMessage.get(Symbol.MSG_OPENING_TCP_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), portDkr.protocol()));
                AsynchronousServerSocketChannel ach = null;
                ServerSocketChannel ch = null;
                if(adr instanceof InetSocketAddress) {
                    if (harbor.netMultiplexer() == Harbor.MultiPlexerType.Pigeon) {
                        ach = AsynchronousServerSocketChannel.open();
                    }
                    else {
                        ch = ServerSocketChannel.open();
                    }
                }
                else {
                    File f = new File(portDkr.socketPath());
                    if(f.exists())
                        f.delete();
                    if (harbor.netMultiplexer() == Harbor.MultiPlexerType.Pigeon) {
                        throw new IOException("Asynchronous mode not supported for UNIX domain socket");
                    }
                    else {
                        ch = SysUtil.openUnixDomainServerSocketChannel();
                    }
                }

                try {
                    if(ch != null) {
                        ch.bind(adr);
                        anchorablePorts.add(new Pair<>(new ServerSocketChannelRudder(ch), portDkr));
                    }
                    else {
                        ach.bind(adr);
                        anchorablePorts.add(new Pair<>(new AsynchronousServerSocketChannelRudder(ach), portDkr));
                    }
                } catch (SocketException e) {
                    BayLog.error(BayMessage.get(Symbol.INT_CANNOT_OPEN_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), e.getMessage()));
                    throw e;
                }
            }
            else {
                BayLog.info(BayMessage.get(Symbol.MSG_OPENING_UDP_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), portDkr.protocol()));
                DatagramChannel ch = DatagramChannel.open();
                try {
                    ch.bind(adr);
                } catch (SocketException e) {
                    BayLog.error(BayMessage.get(Symbol.INT_CANNOT_OPEN_PORT, portDkr.host() == null ? "" : portDkr.host(), portDkr.port(), e.getMessage()));
                    return;
                }
                unanchorablePorts.add(new Pair<>(new DatagramChannelRudder(ch), portDkr));
            }
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

    /**
     * Finds port docker from server socket rudder
     */
    public static Port findAnchorablePort(Rudder rd) {
        for(Pair<Rudder, Port> pair: anchorablePorts) {
            if(pair.a == rd) {
                return pair.b;
            }
        }
        return null;
    }


    ////////////////////////////////////////////////////////////////
    // private methods
    ////////////////////////////////////////////////////////////////
    private static void getHome(String home) throws BayException, IOException {
        // Get BayServer home
        if(home == null)
            home = ".";
        bservHome = new File(home).getAbsoluteFile().getCanonicalPath();
        if(!new File(bservHome).isDirectory())
            throw new BayException("BayServer home is not a directory: " + bservHome);
        if (bservHome.endsWith(File.separator))
            bservHome = bservHome.substring(0, bservHome.length() - 1);
        BayLog.debug("BayServer home: " + bservHome);
    }

    private static void getPlan(String plan) throws BayException, IOException {
        // Get plan file
        if(plan == null)
            plan = "plan/bayserver.plan";
        if(!new File(plan).isAbsolute())
            plan = bservHome + "/" + plan;
        bservPlan = new File(plan).getAbsoluteFile().getCanonicalPath();
        BayLog.debug("BayServer Plan: " + bservPlan);
        if(!new File(bservPlan).exists())
            throw new BayException("Plan file not exists: " + bservPlan);
        if(!new File(bservPlan).isFile())
            throw new BayException("Plan file is not a file: " + bservPlan);

        planDir = new File(bservHome, "plan").getAbsolutePath();
    }

    public static void init() throws BayException, IOException {
        File f = new File(BayServer.bservHome + File.separator + "init.jar");

        // Retrieve init.jar from bayserver.jar
        try (InputStream in = BayServer.class.getResourceAsStream("/init.jar");
             FileOutputStream out = new FileOutputStream(f)) {
            byte buf[] = new byte[1024];
            while(true) {
                int c = in.read(buf);
                if(c == -1)
                    break;
                out.write(buf, 0, c);
            }
        }

        new JarExtractor().extract(f.getPath(), BayServer.bservHome);
        f.delete();
    }
    
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

    //
    // Run train runners and taxi runners
    //
    private static void invokeRunners() {
        TrainRunner.init(harbor.trainRunners());
        TaxiRunner.init(harbor.taxiRunners());
    }
}