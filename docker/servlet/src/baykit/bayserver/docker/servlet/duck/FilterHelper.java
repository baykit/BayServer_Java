package baykit.bayserver.docker.servlet.duck;

import java.io.IOException;
import java.util.Iterator;

/**
 * Helper methods for Filter
 */
public interface FilterHelper {
    //////////////////////////////////////////////////////////////
    // Helper methods for filter
    //////////////////////////////////////////////////////////////
    void initFilter(Object filter, FilterConfigDuck cfg)
            throws ServletExceptionDuck;

    void doFilter(Object filter, Object req, Object res, FilterChainDuck chain)
            throws IOException, ServletExceptionDuck;

    FilterChainDuck newFilterChain(Iterator<Object> iterator, Object last);

}
