package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;

public interface WarpHandler {

    int nextWarpId();

    WarpData newWarpData(int warpId);

    /** Sends request headers to server */
    void sendReqHeaders(Tour tur) throws IOException;

    /** Sends request content to server */
    void sendReqContent(Tour tur, byte[] bytes, int ofs, int len, DataConsumeListener lis) throws IOException;

    /** Send end request to server */
    void sendEndReq(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException;


    /**
     * Verify if protocol is allowed
     */
    void verifyProtocol(String protocol) throws IOException;
}
