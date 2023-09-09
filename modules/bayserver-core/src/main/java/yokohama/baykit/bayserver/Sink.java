package yokohama.baykit.bayserver;

/**
 * Ship sinks!!
 * Exception thrown by some bugs
 */
public class Sink extends Error {

    public Sink() {
        super();
    }

    public Sink(String fmt, Object ...args) {
        super(String.format(args == null ? String.format("%s", fmt) : String.format(fmt, args)) + "(>_<)");
    }
}
