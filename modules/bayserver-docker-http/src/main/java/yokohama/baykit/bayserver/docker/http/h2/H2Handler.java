package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;

public interface H2Handler extends H2CommandHandler {

    /**
     * Send protocol error to client
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}
