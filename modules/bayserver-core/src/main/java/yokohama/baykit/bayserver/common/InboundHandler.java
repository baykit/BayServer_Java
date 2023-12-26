package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;

public interface InboundHandler {

    /**
     * Send HTTP headers to client
     * @param tur
     * @throws IOException
     */
    void sendResHeaders(Tour tur) throws IOException;

    /**
     * Send Contents to client
     * @param tur
     * @param bytes
     * @param ofs
     * @param len
     * @throws IOException
     */
    void sendResContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException;

    /**
     * Send end of contents to client.
     * sendEnd cannot refer Tour instance because it is discarded before call.
     * @param tur
     * @param keepAlive
     * @lis listener
     * @throws IOException
     */
    void sendEndTour(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException;


}
