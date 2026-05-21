package yokohama.baykit.bayserver.docker.phpverse;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.BcfObject;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.fcgi.FcgWarpDocker;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.SysUtil;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PhpVerseDocker — BayServer-side docker that spawns a {@code phpverse}
 * daemon per grand agent and routes dynamic PHP requests to it over
 * FCGI.
 *
 * Java port of the BayServer-for-PHP PhpVerseDocker, adapted to the
 * Java thread model. Implements Recipe α (= "daemon per agent" / D-P)
 * from PHPVerse TIPS.md §2: each grand agent spawns its own phpverse
 * on its own per-agent unix sock. The sock path includes both the
 * JVM PID and the agent ID so:
 *
 * <ul>
 *   <li>Multiple BayServer JVMs on the same host don't collide (PID).</li>
 *   <li>Multiple grand agents inside one JVM don't collide (agentId).</li>
 * </ul>
 *
 * Per-agent state is held in ArrayLists indexed by {@code agentId - 1}
 * so the lookup in {@link #hostAddrFor(int)} is O(1) and free of
 * concurrent-modification hazards once filled. The PHP version uses
 * a static field + fork-on-add for the same effect — see the recipe
 * discussion below.
 *
 * Wire-up:
 * <pre>
 *   [club *.php]
 *       docker phpverse
 *       workers 4
 *       docRoot /path/to/www
 *       scriptBase /path/to/www
 * </pre>
 *
 * PhpVerseDocker extends {@link FcgWarpDocker} and overrides
 * {@link #hostAddrFor(int)} so WarpBase's existing arrive() path
 * routes each agent's tour to the agent's own daemon.
 */
public class PhpVerseDocker extends FcgWarpDocker {

    /** --workers passed to phpverse (per-agent worker pool). */
    private int workers = 4;

    /** --max-requests passed to phpverse (0 = unlimited). */
    private int maxRequests = 0;

    /** --prepend file path (loaded once on worker startup). */
    private String prependFile = null;

    /** Parent directory for the phpverse unix sock; defaults to /tmp. */
    private String socketDir = null;

    /** Path to the phpverse binary; default "phpverse" (= PATH lookup). */
    private String phpverseBin = null;

    /** PHP CLI binary used to exec phpverse; default "php" (= PATH lookup). */
    private String phpBinary = null;

    //////////////////////////////////////////////////////////////////
    // Per-agent daemon state (Recipe α).
    //
    // Indexed by agentId - 1. The lists grow lazily in
    // ensureSpawned(); slots for agent IDs that never call add() stay
    // null. Read access on the hot path (hostAddrFor) reads a
    // pre-populated reference and so is lock-free.
    //////////////////////////////////////////////////////////////////

    /** Spawned phpverse process per agent (agentId - 1 indexed). */
    private final List<Process> phpverseProcesses = new ArrayList<>();

    /** Daemon's unix sock path per agent (agentId - 1 indexed). */
    private final List<String> phpverseSockPaths = new ArrayList<>();

    /** Resolved SocketAddress per agent (agentId - 1 indexed). */
    private final List<SocketAddress> phpverseHostAddrs = new ArrayList<>();

    /** True once a JVM shutdown hook has been registered (idempotent). */
    private boolean shutdownHookRegistered = false;

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        // Parse plan keys via our initKeyVal() first so this.workers et al.
        // are populated before we set up defaults.
        for (BcfObject obj : elm.contentList) {
            if (obj instanceof BcfKeyVal) {
                initKeyVal((BcfKeyVal) obj);
            }
        }

        // Defaults.
        if (socketDir == null) {
            socketDir = System.getProperty("java.io.tmpdir", "/tmp");
        }
        if (phpBinary == null) {
            phpBinary = "php";
        }
        if (phpverseBin == null) {
            // Default: composer-installed location under the BayServer
            // install dir. The outer build (= build.sh tarball assembly,
            // or build-from-src.sh for the bench harness) runs
            // `composer install` in the PhpVerseDocker module and copies
            // the resulting vendor/ tree to <bservHome>/phpverse/vendor/.
            // The plan can override via `phpversebin /abs/path` for
            // dev / debug scenarios.
            phpverseBin = BayServer.bservHome + "/phpverse/vendor/baykit/phpverse/bin/phpverse";
        }

        // WarpBase.init() requires a non-empty host to resolve hostAddr;
        // give it a unix-domain placeholder that's never actually connected
        // to (PhpVerseDocker overrides hostAddrFor() to return per-agent
        // SocketAddresses computed at ensureSpawned() time).
        if (StringUtil.empty(host)) {
            host = ":unix:" + socketDir + "/phpverse-bayserver-placeholder.sock";
            port = -1;
        }

        super.init(elm, parent);

        // Register our per-agent listener — the listener's add(agentId)
        // call drives the daemon spawn for that specific agent.
        GrandAgent.addLifecycleListener(new PhpVerseDocker_AgentListener(this));
    }

    //////////////////////////////////////////////////////
    // Implements DockerBase
    //////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            case "workers":
                workers = Math.max(1, Integer.parseInt(kv.value));
                break;
            case "maxrequests":
                maxRequests = Math.max(0, Integer.parseInt(kv.value));
                break;
            case "prependfile":
                prependFile = kv.value;
                break;
            case "socketdir":
                socketDir = kv.value;
                break;
            case "phpversebin":
                phpverseBin = kv.value;
                break;
            case "php":
                phpBinary = kv.value;
                break;
            default:
                // Fall through to FcgWarpDocker (scriptBase / docRoot) and
                // then WarpBase (destCity / destPort / destTown / maxShips /
                // timeout) and DockerBase.
                return super.initKeyVal(kv);
        }
        return true;
    }

    //////////////////////////////////////////////////////
    // PHPVerse-specific overrides
    //////////////////////////////////////////////////////

    @Override
    public String decorateScriptFilename(String scriptFname) {
        // PHPVerse uses SCRIPT_FILENAME directly as a filesystem path
        // (= require $script). The php-fpm proxy:fcgi:// prefix would
        // make the path unreadable and trip phpverse's 500 error path.
        return scriptFname;
    }

    //////////////////////////////////////////////////////
    // Per-agent hostAddr override
    //////////////////////////////////////////////////////

    @Override
    protected SocketAddress hostAddrFor(int agentId) {
        int idx = agentId - 1;
        if (idx >= 0 && idx < phpverseHostAddrs.size()) {
            SocketAddress a = phpverseHostAddrs.get(idx);
            if (a != null) return a;
        }
        // Should not happen — ensureSpawned() runs on agent.add() before
        // arrive() can be called. Return the placeholder as a last resort
        // so the caller gets a clear "connect refused" failure instead of
        // an NPE here.
        return hostAddr;
    }

    //////////////////////////////////////////////////////
    // Daemon spawn / shutdown
    //////////////////////////////////////////////////////

    /**
     * Called by the per-agent lifecycle listener on agent.add(agentId).
     * Idempotent — a second add() for the same agentId is a no-op.
     */
    public synchronized void ensureSpawned(int agentId) {
        int idx = agentId - 1;
        if (idx < 0) {
            throw new IllegalArgumentException("agentId must be >= 1, got " + agentId);
        }
        // Grow the lists so set(idx, ...) below is valid.
        while (phpverseProcesses.size() <= idx) {
            phpverseProcesses.add(null);
            phpverseSockPaths.add(null);
            phpverseHostAddrs.add(null);
        }
        if (phpverseProcesses.get(idx) != null) {
            return; // already spawned for this agent
        }
        spawnPhpverse(agentId);
    }

    private void spawnPhpverse(int agentId) {
        int idx = agentId - 1;

        // PID + agentId so multiple BayServer JVMs on one host and multiple
        // grand agents inside one JVM both stay collision-free.
        String sockPath = socketDir + "/phpverse-bayserver-"
                + ProcessHandle.current().pid() + "-" + agentId + ".sock";

        // Stale sock cleanup. phpverse refuses to bind() onto an existing file.
        try {
            Files.deleteIfExists(Paths.get(sockPath));
        } catch (IOException e) {
            BayLog.debug(e, "PhpVerseDocker: stale sock cleanup ignored: %s", sockPath);
        }

        // phpverseBin may be a bare command (PATH-resolved) or an absolute
        // path. Validate only when absolute, mirroring the PHP is_file() guard.
        if (phpverseBin != null && phpverseBin.contains("/")) {
            if (!new File(phpverseBin).isFile()) {
                throw new RuntimeException(
                        "PhpVerseDocker: phpverse binary not found at " + phpverseBin);
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(phpBinary);
        cmd.add(phpverseBin);
        cmd.add("--workers");
        cmd.add(Integer.toString(workers));
        cmd.add("--listen");
        cmd.add("unix://" + sockPath);
        cmd.add("--log-level");
        cmd.add("warn");
        if (maxRequests > 0) {
            cmd.add("--max-requests");
            cmd.add(Integer.toString(maxRequests));
        }
        if (prependFile != null) {
            cmd.add("--prepend");
            cmd.add(prependFile);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // Inherit phpverse's stderr/stdout to the JVM's so its --log-level
        // output reaches the operator's terminal.
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(
                    "PhpVerseDocker: failed to spawn phpverse for agent#" + agentId
                            + ": " + String.join(" ", cmd), e);
        }
        // Close stdin (no input).
        try {
            p.getOutputStream().close();
        } catch (IOException ignore) {
        }

        // Wait for phpverse to bind() its listen sock.
        Path sock = Paths.get(sockPath);
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(sock)) {
                break;
            }
            if (!p.isAlive()) {
                int exitCode = -1;
                try {
                    exitCode = p.exitValue();
                } catch (IllegalThreadStateException ignore) {
                }
                throw new RuntimeException(
                        "PhpVerseDocker: phpverse for agent#" + agentId
                                + " exited early (exitcode=" + exitCode
                                + ") before binding sock " + sockPath);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!Files.exists(sock)) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
            throw new RuntimeException(
                    "PhpVerseDocker: phpverse for agent#" + agentId
                            + " did not bind " + sockPath + " within 5s");
        }

        // Resolve the SocketAddress now so the hot path in
        // hostAddrFor(agentId) is a single ArrayList.get().
        SocketAddress addr;
        try {
            addr = SysUtil.getUnixDomainSocketAddress(sockPath);
        } catch (IOException e) {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
            throw new RuntimeException(
                    "PhpVerseDocker: failed to resolve unix sock SocketAddress for "
                            + sockPath, e);
        }

        phpverseProcesses.set(idx, p);
        phpverseSockPaths.set(idx, sockPath);
        phpverseHostAddrs.set(idx, addr);

        BayLog.info(
                "PhpVerseDocker: spawned phpverse for agent#%d (workers=%d, sock=%s)",
                agentId, workers, sockPath);

        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::killAllPhpverse, "phpverse-shutdown"));
            shutdownHookRegistered = true;
        }
    }

    /**
     * Shutdown hook: SIGTERM each agent's daemon, wait up to 3s, SIGKILL,
     * unlink all socks. Safe to call multiple times.
     */
    public synchronized void killAllPhpverse() {
        for (int i = 0; i < phpverseProcesses.size(); i++) {
            Process p = phpverseProcesses.get(i);
            String sock = phpverseSockPaths.get(i);
            if (p != null) {
                killOne(p);
            }
            if (sock != null) {
                try {
                    Files.deleteIfExists(Paths.get(sock));
                } catch (IOException ignore) {
                }
            }
            phpverseProcesses.set(i, null);
            phpverseSockPaths.set(i, null);
            phpverseHostAddrs.set(i, null);
        }
    }

    private void killOne(Process p) {
        if (!p.isAlive()) {
            return;
        }
        p.destroy(); // SIGTERM
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) {
                return;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (p.isAlive()) {
            p.destroyForcibly(); // SIGKILL
        }
    }
}
