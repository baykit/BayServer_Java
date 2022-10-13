package baykit.bayserver;

public class ConfigException extends BayException{

    public String file;
    public int line;

    public ConfigException(String file, int line, Throwable t) {
        super(t);
        this.file = file;
        this.line = line;
    }

    public ConfigException(String file, int line, Throwable t, String fmt, Object ...args) {
        super(t, fmt, args);
        this.file = file;
        this.line = line;
    }

    public ConfigException(String file, int line) {
        this(file, line, null);
    }

    public ConfigException(String file, int line, String fmt, Object ...args) {
        this(file, line, null, fmt, args);
    }

    @Override
    public String getMessage() {
        return createMessage(super.getMessage(), file, line);
    }

    /**
     * Utility method
     */
    public static String createMessage(String msg, String file, int line) {
        return msg + " (" + file + ":" + line + ")";
    }

}
