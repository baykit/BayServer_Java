package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.tour.TourHandler;

import java.io.IOException;

public interface WarpHandler extends TourHandler {

    int nextWarpId();

    WarpData newWarpData(int warpId);

    /**
     * Verify if protocol is allowed
     */
    void verifyProtocol(String protocol) throws IOException;
}
