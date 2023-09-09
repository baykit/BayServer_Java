package yokohama.baykit.bayserver;

public class ConfigException extends BayException{

    public String file;
    public int line;


    public ConfigException(String file, int line, String fmt, Object ...args) {
        super(fmt, args);
        this.file = file;
        this.line = line;
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
