package baykit.bayserver.protocol;

import java.io.IOException;

public class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
