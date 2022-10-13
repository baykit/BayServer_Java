package baykit.bayserver.bcf;

import baykit.bayserver.BayException;
import baykit.bayserver.ConfigException;

public class ParseException extends ConfigException {

    public ParseException(String file, int line, Throwable t) {
        super(file, line, t);
    }

    public ParseException(String file, int line, String msg, Throwable t) {
        super(file, line, msg, t);
    }

    public ParseException(String file, int line) {
        super(file, line);
    }

    public ParseException(String file, int line, String message) {
        super(file, line, message);
    }
}
