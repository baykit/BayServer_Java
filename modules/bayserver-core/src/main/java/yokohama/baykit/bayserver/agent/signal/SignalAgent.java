package yokohama.baykit.bayserver.agent.signal;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.Symbol;
import yokohama.baykit.bayserver.agent.monitor.GrandAgentMonitor;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class SignalAgent{

    public static final String COMMAND_RELOAD_CERT = "reloadcert";
    public static final String COMMAND_MEM_USAGE = "memusage";
    public static final String COMMAND_RESTART_AGENTS = "restartagents";
    public static final String COMMAND_SHUTDOWN = "shutdown";
    public static final String COMMAND_ABORT = "abort";
    public static final String[] commands = {
            COMMAND_RELOAD_CERT,
            COMMAND_MEM_USAGE,
            COMMAND_RESTART_AGENTS,
            COMMAND_SHUTDOWN,
            COMMAND_ABORT
    };
    

    static Map<String, String> signalMap = new HashMap<>();

    public static void init(int port) throws IOException {
        if(port > 0) {
            runSignalAgent(port);
        }
        else {
            SignalProxy sp = SignalProxy.getProxy();
            if (sp != null) {
                for (String cmd : commands) {
                    sp.register(getSignalFromCommand(cmd), () -> handleCommand(cmd));
                }
            }
        }
    }



    private static void handleCommand(String cmd) {
        BayLog.debug("handle command: %s", cmd);
        try {
            switch (cmd.toLowerCase()) {
                case COMMAND_RELOAD_CERT:
                    GrandAgentMonitor.reloadCertAll();
                    break;
                case COMMAND_MEM_USAGE:
                    GrandAgentMonitor.printUsageAll();
                    break;
                case COMMAND_RESTART_AGENTS:
                    try {
                        GrandAgentMonitor.restartAll();
                    } catch (IOException e) {
                        BayLog.error(e);
                    }
                    break;
                case COMMAND_SHUTDOWN:
                    GrandAgentMonitor.shutdownAll();
                    break;
                case COMMAND_ABORT:
                    System.exit(1);
                    break;
                default:
                    BayLog.error("Unknown command: " + cmd);
                    break;
            }
        }
        catch(IOException e) {
            BayLog.error(e);
        }
    }

    public static String getCommandFromSignal(String sig) {
        initSignalMap();
        return signalMap.get(sig);
    }

    public static String getSignalFromCommand(String command) {
        initSignalMap();
        for(String sig: signalMap.keySet()) {
            if(signalMap.get(sig).equalsIgnoreCase(command))
                return sig;
        }
        return null;
    }

    
    private static void initSignalMap() {
        if(!signalMap.isEmpty())
            return;

        if(SysUtil.runOnWindows()) {
            /** Available signals on Windows
             SIGABRT
             SIGFPE
             SIGILL
             SIGINT
             SIGSEGV
             SIGTERM
             */
            signalMap.put("SEGV", COMMAND_RELOAD_CERT);
            signalMap.put("ILL", COMMAND_MEM_USAGE);
            signalMap.put("INT", COMMAND_SHUTDOWN);
            signalMap.put("TERM", COMMAND_RESTART_AGENTS);
            signalMap.put("ABRT", COMMAND_ABORT);
        }
        else {
            signalMap.put("ALRM", COMMAND_RELOAD_CERT);
            signalMap.put("TRAP", COMMAND_MEM_USAGE);
            signalMap.put("HUP", COMMAND_RESTART_AGENTS);
            signalMap.put("TERM", COMMAND_SHUTDOWN);
            signalMap.put("ABRT", COMMAND_ABORT);
        }
    }

    public static void runSignalAgent(int port) {
        new Thread(() -> {
            try (ServerSocketChannel serverSocket = ServerSocketChannel.open()){
                BayLog.info( BayMessage.get(Symbol.MSG_OPEN_CTL_PORT, port));
                serverSocket.bind(new InetSocketAddress("127.0.0.1", port));

                while (true) {
                    SocketChannel s =  serverSocket.accept();
                    try {
                        s.socket().setSoTimeout(5);
                        BufferedReader br = new BufferedReader(new InputStreamReader(s.socket().getInputStream()));
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.socket().getOutputStream()));

                        String line = br.readLine();
                        BayLog.info(BayMessage.get(Symbol.MSG_COMMAND_RECEIVED, line));
                        handleCommand(line);
                        bw.write("OK");
                        bw.newLine();
                        bw.flush();
                    }
                    catch (Exception e) {
                        BayLog.error(e);
                    }
                }
            }
            catch (Throwable e) {
                BayLog.fatal(e);
            }

            System.exit(0);
        }).start();
    }
}
