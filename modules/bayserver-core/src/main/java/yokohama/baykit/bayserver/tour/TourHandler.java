package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;
import java.nio.channels.FileChannel;

public interface TourHandler extends Reusable {

    /**
     * Send HTTP headers to client
     * @param tur
     * @throws IOException
     */
    void sendHeaders(Tour tur) throws IOException;

    /**
     * Send Contents to client.
     *
     * Returns whether the internal write buffer still has room. When false
     * is returned, the caller should stop submitting further content and
     * wait until the internal buffer has room again before resuming.
     *
     * @param tur
     * @param bytes
     * @param ofs
     * @param len
     * @throws IOException
     */
    boolean sendContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException;

    void transferContent(Tour tur, Rudder fileRd, int ofs, int len, DataConsumeListener lis) throws IOException;

    /**
     * Send end of contents to client.
     * @param tur
     * @lis listener
     * @throws IOException
     */
    void sendEndTour(Tour tur, DataConsumeListener lis) throws IOException;

    /**
     * Send protocol error to client
     * @param e
     * @return
     * @throws IOException
     */
    boolean onProtocolError(ProtocolException e) throws IOException;

}
