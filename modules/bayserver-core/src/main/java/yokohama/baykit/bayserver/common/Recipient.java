package yokohama.baykit.bayserver.common;

import java.io.IOException;

/**
 * Letter receiver
 */
public interface Recipient {

    /**
     * Receives letters.
     * @param wait blocking mode
     * @return
     * @throws IOException
     */
    boolean receive(boolean wait) throws IOException;

    /**
     * Wakes up the recipient
     */
    void wakeup();
}
