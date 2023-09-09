package yokohama.baykit.bayserver.bcf;

import yokohama.baykit.bayserver.ConfigException;

public class ParseException extends ConfigException {

    public ParseException(String file, int line, String message) {
        super(file, line, message);
    }
}
