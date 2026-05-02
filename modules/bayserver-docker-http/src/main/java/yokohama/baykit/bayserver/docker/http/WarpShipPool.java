package yokohama.baykit.bayserver.docker.http;

import yokohama.baykit.bayserver.common.WarpShip;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-agent pool of active WarpShips that support H2-style stream
 * multiplexing. Unlike WarpShipStore (which manages physical WarpShip
 * objects and is unaware of tours), this pool reasons about how many
 * tours each ship is currently carrying, so that an arriving tour can
 * be routed to the least-loaded ship with capacity.
 *
 * Lifecycle: HtpWarpDocker adds a ship to the pool when arrive() rents
 * a fresh one (= onShipRented), and removes it when the connection
 * closes (= onEndShip). Tour attach/detach is handled on the ship
 * itself via its tourMap; this pool only routes.
 */
public class WarpShipPool {

    private final List<WarpShip> ships = new ArrayList<>();

    /**
     * Find the WarpShip currently carrying the fewest tours that still has
     * room for one more. Returns null when no eligible ship exists, in
     * which case the caller falls back to opening a fresh connection.
     */
    public synchronized WarpShip findIdlest() {
        WarpShip best = null;
        int bestCount = Integer.MAX_VALUE;
        for (WarpShip ws : ships) {
            // Accept ships whose connect has not completed yet: tours
            // attached before notifyConnect buffer in WarpShip.cmdBuf
            // and flush together once the socket is up. Folding these
            // arrivals onto the in-flight ship avoids the start-up
            // thundering-herd where every parallel arrival opens its
            // own backend connection because no prior ship is "ready".
            if (ws.protocolHandler == null) continue;
            int cap = ws.warpHandler().maxMultiplexedTours();
            int n = ws.tourCount();
            if (n < cap && n < bestCount) {
                best = ws;
                bestCount = n;
                if (n == 0) break; // can't be more idle
            }
        }
        return best;
    }

    public synchronized void add(WarpShip ws) {
        ships.add(ws);
    }

    public synchronized void remove(WarpShip ws) {
        ships.remove(ws);
    }

    public synchronized int size() {
        return ships.size();
    }
}
