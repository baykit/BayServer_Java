package yokohama.baykit.bayserver.docker.ajp;

import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;

public interface AjpHandler extends AjpCommandHandler{

    /**
     * Send protocol error to client
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}

