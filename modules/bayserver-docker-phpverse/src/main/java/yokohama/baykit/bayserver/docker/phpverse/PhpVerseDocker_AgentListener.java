package yokohama.baykit.bayserver.docker.phpverse;

import yokohama.baykit.bayserver.agent.LifecycleListener;

/**
 * Per-agent lifecycle listener for PhpVerseDocker.
 *
 * Recipe α: each grand agent spawns its own phpverse daemon on a
 * per-agent unix sock. add(agentId) drives the spawn; subsequent
 * calls for the same agentId are no-ops via the docker's per-agent
 * state.
 *
 * remove() is a no-op: phpverse keeps running until the BayServer
 * JVM exits. The shutdown hook registered on first
 * {@link PhpVerseDocker#spawnPhpverse(int)} is the kill point and
 * tears down every per-agent daemon.
 *
 * The standard per-agent WarpShipStore is created by WarpBase's own
 * listener (added independently by parent::init()), not by us.
 */
class PhpVerseDocker_AgentListener implements LifecycleListener {

    private final PhpVerseDocker docker;

    PhpVerseDocker_AgentListener(PhpVerseDocker docker) {
        this.docker = docker;
    }

    @Override
    public void add(int agtId) {
        docker.ensureSpawned(agtId);
    }

    @Override
    public void remove(int agtId) {
        // No-op. Daemon lives until JVM exit (shutdown hook).
    }
}
