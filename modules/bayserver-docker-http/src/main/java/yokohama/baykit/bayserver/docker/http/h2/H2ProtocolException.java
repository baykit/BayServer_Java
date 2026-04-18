package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.ProtocolException;

/**
 * A {@link ProtocolException} that carries the specific HTTP/2 error code
 * that should appear in the resulting GOAWAY frame. The base class always
 * maps to {@link H2ErrorCode#PROTOCOL_ERROR}; this subclass lets call
 * sites pick FLOW_CONTROL_ERROR, COMPRESSION_ERROR, etc.
 */
public class H2ProtocolException extends ProtocolException {
    public final int errorCode;

    public H2ProtocolException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
