package yokohama.baykit.bayserver.common;

public interface Valve {
    void openReadValve();
    void openWriteValve();
    void destroy();
}
