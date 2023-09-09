package yokohama.baykit.bayserver.docker.servlet.duck;

import java.util.Enumeration;
import java.util.Vector;

public class HttpSessionContextDuck {

    /**
     * Deprecated. Return the empty collection
     */
    public Enumeration getIds() {
        return new Vector().elements();
    }
}
