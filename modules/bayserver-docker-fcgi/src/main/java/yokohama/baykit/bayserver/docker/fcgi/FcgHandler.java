package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;

public interface FcgHandler extends FcgCommandHandler{

    /**
     * Send protocol error to client
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}

