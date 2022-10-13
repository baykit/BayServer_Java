package baykit.bayserver.agent.signal;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.BayServer;
import baykit.bayserver.Symbol;
import baykit.bayserver.bcf.*;
import baykit.bayserver.docker.builtin.BuiltInHarborDocker;
import baykit.bayserver.util.SysUtil;

import java.io.*;
import java.net.Socket;

public class SignalSender {

    int bayPort = BuiltInHarborDocker.DEFAULT_CONTROL_PORT;
    String pidFile = BuiltInHarborDocker.DEFAULT_PID_FILE;

    /**
     * Send running BayServer a command
     * @param cmd
     * @throws IOException
     */
    public void sendCommand(String cmd) throws ParseException, IOException {
        parseBayPort(BayServer.bservPlan);
        if(bayPort  < 0) {
            long pid = readPidFile();
            String sig = SignalAgent.getSignalFromCommand(cmd);
            if(sig == null)
                throw new IllegalArgumentException("Invalid command: " + cmd);
            if(pid <= 0)
                throw new IllegalArgumentException("Invalid process ID: " + pid);
            kill(pid, sig);
        }
        else {
            BayLog.info(BayMessage.get(Symbol.MSG_SENDING_COMMAND, cmd));
            send("localhost", bayPort, cmd);
        }
    }

    /**
     * Parse plan file and get port number of SignalAgent
     * @return
     */
    void parseBayPort(String plan) throws ParseException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(plan);
        for(BcfObject o : doc.contentList) {
            if(o instanceof BcfElement) {
                BcfElement elm = (BcfElement) o;
                if(elm.name.equalsIgnoreCase("harbor")) {
                    for(BcfObject o2 : elm.contentList) {
                        if (o2 instanceof BcfKeyVal) {
                            BcfKeyVal kv = (BcfKeyVal)o2;
                            if(kv.key.equalsIgnoreCase("controlPort"))
                                bayPort = Integer.parseInt(kv.value);
                            else if(kv.key.equalsIgnoreCase("pidFile"))
                                pidFile = kv.value;
                        }
                    }
                }
            }
        }
    }

    /**
     * Send another BayServer running host:port a command
     * @param host
     * @param port
     * @param cmd
     * @throws IOException
     */
    void send(String host, int port, String cmd) throws IOException {
        try (Socket s = new Socket(host, port)) {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            bw.write(cmd);
            bw.newLine();
            bw.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            br.readLine();
        }
    }


    void kill(long pid, String signal) throws IOException {
        ProcessBuilder pb;
        if( SysUtil.runOnWindows()) {
            pb = new ProcessBuilder("taskkill", "/PID", Long.toString(pid), "/F");
        }
        else {
            pb = new ProcessBuilder("kill", "-" + signal, Long.toString(pid));
        }
        Process p = pb.start();
        printOutput(p.getInputStream());
        printOutput(p.getErrorStream());

        synchronized (p) {
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                BayLog.error(e);
            }
        }
    }

    long readPidFile() throws IOException {

        try(FileInputStream in = new FileInputStream(BayServer.getLocation(pidFile))) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            return Long.parseLong(r.readLine());
        }
    }


    void printOutput(InputStream in) throws IOException {
        BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(in));
        while(true) {
            String line = r.readLine();
            if(line == null)
                break;

            BayLog.error(line);
        }
    }
}
