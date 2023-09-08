package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;

import java.util.ArrayList;

public interface Town {

    /**
     * Get the name (path) of this town
     * The name ends with "/"
     * @return
     */
    String name();

    /**
     * Get city
     * @return
     */
    City city();

    /**
     * Get the physical location of this town
     * @return
     */
    String location();

    /**
     * Get index file
     * @return
     */
    String welcomeFile();


    /**
     * All clubs in this town
     * @return club list
     */
    ArrayList<Club> clubs();

    /**
     * Get rerouted uri
     * @return reroute list
     */
    String reroute(String uri);

    enum MatchType {
        MATCHED, NOT_MATCHED, CLOSE
    }
    MatchType matches(String uri);

    void checkAdmitted(Tour tour) throws HttpException;
    
    
}
