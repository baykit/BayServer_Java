package yokohama.baykit.bayserver.protocol;

import java.io.IOException;

/**
 * ProtocolException is thrown when protocol-level violations are detected,
 * such as invalid packet framing or incorrect packet ordering.
 * (Invalid HTTP headers or content length values result in an HttpException,
 * which causes a 400 Bad Request response to be returned to the client.)
 */
public class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
