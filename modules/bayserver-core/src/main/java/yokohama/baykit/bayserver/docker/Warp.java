package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.ship.Ship;

public interface Warp extends Club {

    String host();
    int port();
    String warpBase();
    int timeoutSec();
    void keep(Ship warpShip);
    void onEndShip(Ship warpShip);

    /**
     * Mark a ship as no longer eligible for sharing new tours (= remove
     * from the multiplex pool, if any). Used by H2 warp handlers when
     * the backend signals GOAWAY: in-flight tours can still drain on
     * the existing connection, but no new tours should be attached.
     * Default no-op for non-multiplex dockers.
     */
    default void excludeFromPool(Ship warpShip) {
    }
}
