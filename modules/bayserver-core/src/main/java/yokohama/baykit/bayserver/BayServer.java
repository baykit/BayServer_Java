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

    /**
     * Anchorable (TCP / UNIX) ports.
     *
     * <p>Each entry stores a list of listening channels plus the Port docker.
     * When SO_REUSEPORT is enabled the list holds {@code grandAgents}
     * independent {@link ServerSocketChannel}s bound to the same address; each
     * GrandAgent registers only its own channel with its selector so that the
     * kernel, not a user-space thundering herd, decides which agent receives
     * each incoming connection.  On platforms without SO_REUSEPORT (and for
     * the async/UNIX-domain paths where we do not apply it) the list contains
     * a single shared channel and every agent registers the same one.</p>
     */
    public static final ArrayList<Pair<List<Channel>, Port>> anchorablePorts = new ArrayList<>();

    public static final ArrayList<Pair<Channel, Port>> unanchorablePorts = new ArrayList<>();

    /**
     * Mapping from GrandAgent id to the index of the listening channel it
     * should register inside each {@link #anchorablePorts} entry.
     *
     * <p>Initially each agent {@code i} is mapped to index {@code i - 1}, so
     * with SO_REUSEPORT the N initial agents own N distinct channels.  When
     * an agent aborts, {@link yokohama.baykit.bayserver.agent.monitor.GrandAgentMonitor#agentAborted}
     * removes the entry and reassigns the same channel index to the next
     * agent id about to be created, so the replacement inherits the bound
     * socket without any array manipulation.</p>
     *
     * <p>When a port has only one listener (no SO_REUSEPORT) callers should
     * take {@code index % listeners.size()} so every agent lands on the
     * shared channel.</p>
     */
    public static final Map<Integer, Integer> agentIdToChannelIndex = new HashMap<>();

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
            RudderStateStore.init();
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
                try {
                    List<Channel> listeners = openAnchorableListeners(portDkr, adr);
                    anchorablePorts.add(new Pair<>(listeners, portDkr));
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
                unanchorablePorts.add(new Pair<>(ch, portDkr));
            }
        }

        // Seed agentIdToChannelIndex for the anchorable (TCP) agents.  This
        // map is consulted by anchorableListenersFor(agentId) to pick one
        // of the N SO_REUSEPORT-backed ServerSocketChannels (or the single
        // shared channel on platforms without SO_REUSEPORT).
        //
        // When there is an unanchorable (UDP / H3) port, GrandAgentMonitor
        // spawns a dedicated agent at id 1 for it and the anchorable agents
        // start at id 2.  That UDP agent never calls
        // anchorableListenersFor, so it must not be represented in the
        // map -- otherwise the TCP agents would all be shifted by one and
        // the last one would read past the end of the listener list.
        int udpAgentOffset = unanchorablePorts.isEmpty() ? 0 : 1;
        for (int i = 1; i <= harbor.grandAgents(); i++) {
            agentIdToChannelIndex.put(udpAgentOffset + i, i - 1);
        }
    }

    /**
     * Open the listening channels that back an anchorable Port docker.
     *
     * <p>Two layouts are possible:</p>
     * <ul>
     *   <li><b>SO_REUSEPORT</b> — when the OS supports it and the port is a
     *       regular TCP endpoint driven by a selector-based multiplexer, we
     *       open {@code grandAgents()} independent {@link ServerSocketChannel}s,
     *       each bound to the same address with SO_REUSEPORT set.  Each
     *       GrandAgent later registers exactly one of these channels, so the
     *       kernel distributes incoming connections across agents by hashing
     *       the 4-tuple rather than letting all agents race on a single
     *       accept queue.</li>
     *   <li><b>Single shared channel</b> — UNIX-domain sockets, the async
     *       (Pigeon) multiplexer and platforms without SO_REUSEPORT fall back
     *       to the legacy layout: one channel, every agent registers it.</li>
     * </ul>
     */
    private static List<Channel> openAnchorableListeners(Port portDkr, SocketAddress adr) throws IOException {
        List<Channel> listeners = new ArrayList<>();

        boolean tcpSelector = adr instanceof InetSocketAddress
                && harbor.netMultiplexer() != Harbor.MultiPlexerType.Pigeon;

        if (tcpSelector && SysUtil.supportReusePort() && harbor.grandAgents() > 1) {
            for (int i = 0; i < harbor.grandAgents(); i++) {
                ServerSocketChannel ch = ServerSocketChannel.open();
                ch.setOption(StandardSocketOptions.SO_REUSEPORT, true);
                ch.bind(adr);
                listeners.add(ch);
            }
            return listeners;
        }

        // Fall back: open exactly one listener shared by all agents.
        Channel shared;
        if (adr instanceof InetSocketAddress) {
            if (harbor.netMultiplexer() == Harbor.MultiPlexerType.Pigeon) {
                AsynchronousServerSocketChannel ach = AsynchronousServerSocketChannel.open();
                ach.bind(adr);
                shared = ach;
            } else {
                ServerSocketChannel ch = ServerSocketChannel.open();
                ch.bind(adr);
                shared = ch;
            }
        } else {
            File f = new File(portDkr.socketPath());
            if (f.exists()) f.delete();
            if (harbor.netMultiplexer() == Harbor.MultiPlexerType.Pigeon) {
                throw new IOException("Asynchronous mode not supported for UNIX domain socket");
            }
            ServerSocketChannel ch = SysUtil.openUnixDomainServerSocketChannel();
            ch.bind(adr);
            shared = ch;
        }
        listeners.add(shared);
        return listeners;
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
    public static Port findAnchorablePort(Channel ch) {
        for(Pair<List<Channel>, Port> pair: anchorablePorts) {
            for(Channel c: pair.a) {
                if(c == ch) {
                    return pair.b;
                }
            }
        }
        return null;
    }

    /**
     * Resolve the (listening channel, Port docker) pairs that the given
     * GrandAgent should register with its selector.
     *
     * <p>When SO_REUSEPORT is active each agent owns one of the N bound
     * listeners, selected through {@link #agentIdToChannelIndex}.  Otherwise
     * every agent shares the same single listener per port.</p>
     */
    public static List<Pair<Channel, Port>> anchorableListenersFor(int agentId) {
        int idx = agentIdToChannelIndex.get(agentId);
        boolean reusePort = SysUtil.supportReusePort();
        List<Pair<Channel, Port>> result = new ArrayList<>();
        for (Pair<List<Channel>, Port> pair : anchorablePorts) {
            List<Channel> listeners = pair.a;
            Channel ch = reusePort ? listeners.get(idx) : listeners.get(0);
            result.add(new Pair<>(ch, pair.b));
        }
        return result;
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