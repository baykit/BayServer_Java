package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;

public interface TourHandler extends Reusable {

    /**
     * Send HTTP headers to client
     * @param tur
     * @throws IOException
     */
    void sendHeaders(Tour tur) throws IOException;

    /**
     * Send Contents to client
     * @param tur
     * @param bytes
     * @param ofs
     * @param len
     * @throws IOException
     */
    void sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException;

    /**
     * Send end of contents to client.
     * @param tur
     * @param keepAlive
     * @lis listener
     * @throws IOException
     */
    void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException;

    /**
     * Send protocol error to client
     * @param e
     * @return
     * @throws IOException
     */
    boolean onProtocolError(ProtocolException e) throws IOException;
}
