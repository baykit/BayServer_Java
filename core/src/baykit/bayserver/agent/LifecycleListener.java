package baykit.bayserver.agent;

public interface LifecycleListener {
    void add(int agentId);

    void remove(int agentId);
}
