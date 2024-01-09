package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.protocol.ProtocolException;

import java.io.IOException;

public interface H1Handler extends H1CommandHandler{

    /**
     * Send protocol error to client
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}
