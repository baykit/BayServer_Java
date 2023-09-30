package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;

import java.util.List;

public interface City {

    /**
     * City name (host name)
     * @return
     */
    String name();

    /**
     * All clubs (not included in town) in this city
     * @return
     */
    List<Club> clubs();


    /**
     * All towns in this city
     * @return
     */
    List<Town> towns();

    /**
     * Enter city
     * @param tour
     */
    void enter(Tour tour) throws HttpException;

    /**
     * Get trouble docker
     * @return
     */
    Trouble getTrouble();

    /**
     * Logging
     * @param tour
     */
    void log(Tour tour);
}
