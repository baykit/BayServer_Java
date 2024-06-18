package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;

public interface QicHandler extends QicCommandHandler{

    /**
     * Send protocol error to client
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}
