package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;

import java.util.List;

public interface City {

    /**
     * City name (host name)
     */
    String name();

    /**
     * All clubs (not included in town) in this city
     */
    List<Club> clubs();

    /**
     * All towns in this city
     */
    List<Town> towns();

    /**
     * Enter city
     */
    void enter(Tour tour) throws HttpException;

    /**
     * Get trouble docker
     */
    Trouble getTrouble();

    /**
     * Logging
     */
    void log(Tour tour);
}
