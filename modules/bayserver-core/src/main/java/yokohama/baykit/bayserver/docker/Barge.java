package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.Pair;

/**
 * "Barge" is a metaphor for the cache management function.
 */
public interface Barge {

    /**
     * "Cargo" is a metaphor for cached data.
     */
    interface Cargo {

        String path();
        Headers headers();

        byte[] content();
        int length();

        boolean onBarge();
        boolean exceeded();

        void saveHeaders(Headers headers);
        void saveContent(byte[] bytes, int offset, int len);
        void endSave();

        void releaseRudder(Rudder rudder);
    }

    /**
     * Barge name (path)
     */
    String name();

    /**
     * Capacity of the barge. (in mega-bytes)
     */
    int capacity();

    /**
     * Get cargo on the barge.
     */

    Pair<Cargo, Rudder> getCargo(Tour tour);
}
