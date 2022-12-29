package baykit.bayserver.bcf;

import baykit.bayserver.BayException;
import baykit.bayserver.ConfigException;

public class ParseException extends ConfigException {

    public ParseException(String file, int line, String message) {
        super(file, line, message);
    }
}
