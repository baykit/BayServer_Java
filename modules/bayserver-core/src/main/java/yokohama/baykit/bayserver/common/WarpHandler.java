package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.DataConsumeListener;

import java.io.IOException;

public interface WarpHandler {

    int nextWarpId();

    WarpData newWarpData(int warpId);

    void postWarpHeaders(Tour tur) throws IOException;

    void postWarpContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException;

    void postWarpEnd(Tour tur) throws IOException;

    /**
     * Verify if protocol is allowed
     */
    void verifyProtocol(String protocol) throws IOException;
}
