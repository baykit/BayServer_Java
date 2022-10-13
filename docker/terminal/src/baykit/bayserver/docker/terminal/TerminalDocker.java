package baykit.bayserver.docker.terminal;

import baykit.bayserver.*;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.Town;
import baykit.bayserver.docker.dockerbase.ClubBase;
import baykit.bayserver.util.HttpStatus;
import baykit.bayserver.util.HttpWarpUtil;
import baykit.bayserver.util.StringUtil;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class TerminalDocker extends ClubBase {

    public static final int RETRY_COUNT = 5;
    int curTrackNo;

    int startPort;
    String environment;
    String railsHome;
    String interpreter;
    String urlRoot;
    ArrayList<Process> railsProcesses = new ArrayList<>();

    ThreadLocal<Integer> trackNo = ThreadLocal.withInitial(() -> -1);

    static class RailsOutputCatcher extends Thread {
        final InputStream in;
        final int trackNo;

        RailsOutputCatcher(InputStream in, int trackNo, boolean stdout) {
            super("RailsOutputCatcher#" + trackNo + (stdout ? "-stdout" : "-stderr"));
            this.in = in;
            this.trackNo = trackNo;
        }

        @Override
        public void run() {
            BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(in));
            try {
                while(true) {
                    String line = br.readLine();
                    if(line == null)
                        break;
                    BayServer.debug("Rails#" + trackNo + ":" + line);
                }
            }
            catch(IOException e) {
                BayServer.error(e);
            }
        }


    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // override methods                                                                     //
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void init(BcfElement ini, Docker parent, BayServer server) throws BayException {
        super.init(ini, parent, server);

        if(parent == null)
            throw new BayException("Terminal docker must be located in town");

        for (Object o : ini.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                if(kv.key.equalsIgnoreCase("startPort")) {
                    try {
                        startPort = Integer.parseInt(kv.value);
                    }
                    catch(NumberFormatException e) {
                        throw new BayException(e);
                    }
                }
                else if(kv.key.equalsIgnoreCase("environment")) {
                    environment = kv.value;
                }
                else if(kv.key.equalsIgnoreCase("railsHome")) {
                    railsHome = kv.value;
                }
                else if(kv.key.equalsIgnoreCase("interpreter")) {
                    interpreter = kv.value;
                }
            }
        }

        if(startPort <= 0)
            startPort = 3000;
        if(StringUtil.empty(environment))
            environment = "production";
        if(StringUtil.empty(railsHome))
            throw new BayException("Specify railsHome");
        if(StringUtil.empty(interpreter)) {
            String path = System.getenv("PATH");
            StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
            while(st.hasMoreTokens()) {
                String pathElm = st.nextToken();
                if(!pathElm.endsWith(File.separator))
                    pathElm += File.separator;
                File rubyFile = new File(pathElm + "ruby");
                if(rubyFile.isFile() && rubyFile.canExecute()) {
                    interpreter = rubyFile.getPath();
                    break;
                }
            }
            if(StringUtil.empty(interpreter))
                throw new BayException("Specify interpreter");
        }

        urlRoot = ((Town)parent).getName();
        if(!urlRoot.endsWith("/"))
            urlRoot += "/";

        BayServer.debug("Terminal docker");
        BayServer.debug(" startPort=" + startPort);
        BayServer.debug(" environment=" + environment);
        BayServer.debug(" railsHome=" + railsHome);
        BayServer.debug(" interpreter=" + interpreter);
        BayServer.debug(" RAILS_RELATIVE_URL_ROOT=" + urlRoot);
    }

    @Override
    public void arrive(Tour tour, Town town) throws BayException, IOException {
        if(trackNo.get() == -1) {
            synchronized (this) {
                trackNo.set(++curTrackNo);
            }
        }

        int tno = trackNo.get();
        int port = startPort + tno - 1;
        BayServer.debug( "terminal: arrive" + tour.ship + " track#" + tno + " port#" + port);

        Socket s = null;

        while(true) {
            Process p = null;
            if(tno - 1 < railsProcesses.size())
                p = railsProcesses.get(tno - 1);
            if(p == null || !p.isAlive()) {
                BayServer.debug("terminal: track#" + tno + ": Start rails server");
                p = execRails(port);
                synchronized (railsProcesses) {
                    while(railsProcesses.size() < tno)
                        railsProcesses.add(null);
                }
                railsProcesses.set(tno - 1, p);
                new RailsOutputCatcher(p.getInputStream(), tno, true).start();
                new RailsOutputCatcher(p.getErrorStream(), tno, false).start();
            }

            for(int i = 0; i < RETRY_COUNT; i++) {
                BayServer.debug("terminal: track#" + tno + ": Connecting to rails server");
                try {
                    s = new Socket("localhost", port);
                    break;
                }
                catch(IOException e) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                    }
                }
            }

            if(s == null)
                throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, "track#" + tno + ": Could not connect to rails server (port=" + port + ")");
            break;
        }

        HttpWarpUtil warper = new HttpWarpUtil("localhost", port, urlRoot);
        warper.warp(s, tour.ship, town);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // private methods                                                                      //
    //////////////////////////////////////////////////////////////////////////////////////////
    private Process execRails(int port) throws HttpException {
        ProcessBuilder pb = new ProcessBuilder("rails", "s", "-e", environment, "-p", Integer.toString(port));
        //pb.environment().clear();
        pb.directory(new File(railsHome));
        pb.environment().put("RAILS_RELATIVE_URL_ROOT", urlRoot);
        try {
            return pb.start();
        }
        catch(IOException e) {
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot execute rails", e);
        }
    }
}
