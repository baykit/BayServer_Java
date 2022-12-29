package baykit.bayserver;

public class BayException extends Exception {

    public BayException(String fmt, Object ...args) {
        super(
                (args == null || args.length == 0) ?
                        (fmt == null ? null : String.format("%s", fmt)) :
                        String.format(fmt, args));
    }
}
